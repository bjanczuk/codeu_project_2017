// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package codeu.chat.client.commandline;

import java.util.Scanner;

import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.HashMap;

import java.lang.StringBuilder;

import codeu.chat.client.ClientContext;
import codeu.chat.client.Controller;
import codeu.chat.client.View;
import codeu.chat.common.ConversationSummary;
import codeu.chat.util.Logger;

import codeu.chat.common.User;

// Chat - top-level client application.
public final class Chat {

  private final static Logger.Log LOG = Logger.newLog(Chat.class);

  private static final String PROMPT = ">>";

  private static final String BOT_NAME = "Bot";

  private static final String BOT_CONVO = "Convo with Bot";

  private static String response = "";

  private static final Set<String> allPhraseSet = new HashSet<String>();

  private static final List<String> allPhraseList = new ArrayList<String>();

  private static final HashMap<String, ArrayList<String>> allPhraseMap = new HashMap<String, ArrayList<String>>();

  private static final Random generator = new Random();

  private final int SWITCH_NAME = 1, SWITCH_YOUTOO = 2; // variables used for a bitmask

  private final static int PAGE_SIZE = 10;

  private boolean alive = true;

  private final ClientContext clientContext;

  // Constructor - sets up the Chat Application
  public Chat(Controller controller, View view) {
    clientContext = new ClientContext(controller, view);
  }

  // Print help message.
  private static void help() {
    System.out.println("Chat commands:");
    System.out.println("   exit      - exit the program.");
    System.out.println("   help      - this help message.");
    System.out.println("   sign-in <username>  - sign in as user <username>.");
    System.out.println("   sign-out  - sign out current user.");
    System.out.println("   current   - show current user, conversation, message.");
    System.out.println("User commands:");
    System.out.println("   u-add <name>  - add a new user.");
    System.out.println("   u-list-all    - list all users known to system.");
    System.out.println("Conversation commands:");
    System.out.println("   c-add <title>    - add a new conversation.");
    System.out.println("   c-list-all       - list all conversations known to system.");
    System.out.println("   c-select <index> - select conversation from list.");
    System.out.println("Message commands:");
    System.out.println("   m-add <body>     - add a new message to the current conversation.");
    System.out.println("   m-list-all       - list all messages in the current conversation.");
    System.out.println("   m-next <index>   - index of next message to view.");
    System.out.println("   m-show <count>   - show next <count> messages.");
  }

  // Prompt for new command.
  private void promptForCommand() {
    System.out.print(PROMPT);
  }

  // Parse and execute a single command.
  private void doOneCommand(Scanner lineScanner) {

    final Scanner tokenScanner = new Scanner(lineScanner.nextLine());
    if (!tokenScanner.hasNext()) {
      return;
    }
    final String token = tokenScanner.next();

    if (token.equals("exit")) {

      alive = false;

    } else if (token.equals("help")) {

      help();

    } else if (token.equals("sign-in")) {

      if (!tokenScanner.hasNext()) {
        System.out.println("ERROR: No user name supplied.");
      } else {
        signInUser(tokenScanner.nextLine().trim());

        // automatically create the bot user and the conversation with the bot once a user signs in
        if (clientContext.user.lookup(BOT_NAME) == null) // check whether the bot and convo already exist
        {
          addUser(BOT_NAME);
          clientContext.conversation.startConversation(BOT_CONVO, clientContext.user.getCurrent().id);
        }
      }

    } else if (token.equals("sign-out")) {

      if (!clientContext.user.hasCurrent()) {
        System.out.println("ERROR: Not signed in.");
      } else {
        signOutUser();
      }

    } else if (token.equals("current")) {

      showCurrent();

    } else if (token.equals("u-add")) {

      if (!tokenScanner.hasNext()) {
        System.out.println("ERROR: Username not supplied.");
      } else {
        addUser(tokenScanner.nextLine().trim());
      }

    } else if (token.equals("u-list-all")) {

      showAllUsers();

    } else if (token.equals("c-add")) {

      if (!clientContext.user.hasCurrent()) {
        System.out.println("ERROR: Not signed in.");
      } else {
        if (!tokenScanner.hasNext()) {
          System.out.println("ERROR: Conversation title not supplied.");
        } else {
          final String title = tokenScanner.nextLine().trim();
          clientContext.conversation.startConversation(title, clientContext.user.getCurrent().id);
        }
      }

    } else if (token.equals("c-list-all")) {

      clientContext.conversation.showAllConversations();

    } else if (token.equals("c-select")) {

      selectConversation(lineScanner);

    } else if (token.equals("m-add")) {

      if (!clientContext.user.hasCurrent()) {
        System.out.println("ERROR: Not signed in.");
      } else if (!clientContext.conversation.hasCurrent()) {
        System.out.println("ERROR: No conversation selected.");
      } else {
        if (!tokenScanner.hasNext()) {
          System.out.println("ERROR: Message body not supplied.");
        } else {
          final String body = tokenScanner.nextLine().trim();
          clientContext.message.addMessage(clientContext.user.getCurrent().id,
              clientContext.conversation.getCurrentId(),
              body);

            respondAsBot(body);
        }
      }

    } else if (token.equals("m-list-all")) {

      if (!clientContext.conversation.hasCurrent()) {
        System.out.println("ERROR: No conversation selected.");
      } else {
        clientContext.message.showAllMessages();
      }

    } else if (token.equals("m-next")) {

      if (!clientContext.conversation.hasCurrent()) {
        System.out.println("ERROR: No conversation selected.");
      } else if (!tokenScanner.hasNextInt()) {
        System.out.println("Command requires an integer message index.");
      } else {
        clientContext.message.selectMessage(tokenScanner.nextInt());
      }

    } else if (token.equals("m-show")) {

      if (!clientContext.conversation.hasCurrent()) {
        System.out.println("ERROR: No conversation selected.");
      } else {
        final int count = (tokenScanner.hasNextInt()) ? tokenScanner.nextInt() : 1;
        clientContext.message.showMessages(count);
      }

    } else {

      System.out.format("Command not recognized: %s\n", token);
      System.out.format("Command line rejected: %s%s\n", token,
          (tokenScanner.hasNext()) ? tokenScanner.nextLine() : "");
      System.out.println("Type \"help\" for help.");
    }
    tokenScanner.close();
  }

  // Sign in a user.
  private void signInUser(String name) {
    if (!clientContext.user.signInUser(name)) {
      System.out.println("Error: sign in failed (invalid name?)");
    }
  }

  // Sign out a user.
  private void signOutUser() {
    if (!clientContext.user.signOutUser()) {
      System.out.println("Error: sign out failed (not signed in?)");
    }
  }

  // Helper for showCurrent() - show message info.
  private void showCurrentMessage() {
    if (clientContext.conversation.currentMessageCount() == 0) {
      System.out.println(" -- no messages in conversation --");
    } else {
      System.out.format(" conversation has %d messages.\n",
                        clientContext.conversation.currentMessageCount());
      if (!clientContext.message.hasCurrent()) {
        System.out.println(" -- no current message --");
      } else {
        System.out.println("\nCurrent Message:");
        clientContext.message.showCurrent();
      }
    }
  }

  // Show current user, conversation, message, if any
  private void showCurrent() {
    boolean displayed = false;
    if (clientContext.user.hasCurrent()) {
      System.out.println("User:");
      clientContext.user.showCurrent();
      System.out.println();
      displayed = true;
    }

    if (clientContext.conversation.hasCurrent()) {
      System.out.println("Conversation:");
      clientContext.conversation.showCurrent();

      showCurrentMessage();

      System.out.println();
      displayed = true;
    }

    if (!displayed) {
      System.out.println("No current user or conversation.");
    }
  }

  // Display current user.
  private void showCurrentUser() {
    if (clientContext.user.hasCurrent()) {
      clientContext.user.showCurrent();
    } else {
      System.out.println("No current user.");
    }
  }

  // Display current conversation.
  private void showCurrentConversation() {
    if (clientContext.conversation.hasCurrent()) {
      clientContext.conversation.showCurrent();
    } else {
      System.out.println(" No current conversation.");
    }
  }

  // Add a new user.
  private void addUser(String name) {
    clientContext.user.addUser(name);
  }

  // Display all users known to server.
  private void showAllUsers() {
    clientContext.user.showAllUsers();
  }

  public boolean handleCommand(Scanner lineScanner) {

    try {
      promptForCommand();
      doOneCommand(lineScanner);
    } catch (Exception ex) {
      System.out.println("ERROR: Exception during command processing. Check log for details.");
      LOG.error(ex, "Exception during command processing");
    }

    // "alive" may have been set to false while executing a command. Return
    // the result to signal if the user wants to keep going.

    return alive;
  }

  public void selectConversation(Scanner lineScanner) {

    clientContext.conversation.updateAllConversations(false);
    final int selectionSize = clientContext.conversation.conversationsCount();
    System.out.format("Selection contains %d entries.\n", selectionSize);

    final ConversationSummary previous = clientContext.conversation.getCurrent();
    ConversationSummary newCurrent = null;

    if (selectionSize == 0) {
      System.out.println("Nothing to select.");
    } else {
      final ListNavigator<ConversationSummary> navigator =
          new ListNavigator<ConversationSummary>(
              clientContext.conversation.getConversationSummaries(),
              lineScanner, PAGE_SIZE);
      if (navigator.chooseFromList()) {
        newCurrent = navigator.getSelectedChoice();
        clientContext.message.resetCurrent(newCurrent != previous);
        System.out.format("OK. Conversation \"%s\" selected.\n", newCurrent.title);
      } else {
        System.out.println("OK. Current Conversation is unchanged.");
      }
    }
    if (newCurrent != previous) {
      clientContext.conversation.setCurrent(newCurrent);
      clientContext.conversation.updateAllConversations(true);
    }
  }

  private void respondAsBot(String body) {
    // check if the current conversation is with the bot and respond accordingly
    final ConversationSummary current = clientContext.conversation.getCurrent();
    if (current.title.equals(BOT_CONVO))
    {
      final User bot = clientContext.user.lookup(BOT_NAME);

      String response = chooseBotMessage(body);
      System.out.println(response);

      if (response.length() >= 1 && !allPhraseMap.containsKey(response)) { // create a pair for the map if it's not already there
        ArrayList<String> blankList = new ArrayList<String>();
        allPhraseMap.put(response, blankList);
      }

      clientContext.message.addMessage(bot.id,
      clientContext.conversation.getCurrentId(),
      response); // send the String returned by the method back as the bot response
    }
  }

  private String chooseBotMessage(String body) {
    int randResponse;
    int options = 1; // set the bitmask to always switch names at first

    Set<String> mostRecent = splitMessage(body); // split the user's message by sentence/phrase and store the results
    String curUser = clientContext.user.getCurrent().name;

    if (clientContext.conversation.currentMessageCount() <= 1) { // send a generic response if this is the beginning of the conversation
      return response = "Hey " + curUser + "! I'm the Bot :P";
    }

    // check for several standard messages
    else if (mostRecentContainsSubstring("How are you?", mostRecent)) {
      return response = "I'm good, " + curUser + ", thanks. How are you?";
    }

    else if (response.equals("I'm good, " + curUser + ", thanks. How are you?")) {
      return response = "Cool, that's good to hear";
    }

    else if (mostRecentContainsSubstring("What's up?", mostRecent) || mostRecentContainsSubstring("What are you doing?", mostRecent)) {
      return response = "Just hanging out! What about you?";
    }

    // respond with a "you too" if possible
    else if (allPhraseList.get(allPhraseList.size() - 1).equals(response) == false &&
             canAddYouToo(allPhraseList.get(allPhraseList.size() - 1))) {
      options |= SWITCH_YOUTOO;
      return response = adjustMessages(allPhraseList.get(allPhraseList.size() - 1), curUser, options);
    }

    // let the user know that the bot doesn't have enough phrases to pick from
    else if (allPhraseList.size() <= 1 ||
            clientContext.conversation.currentMessageCount() > 4 && allPhraseList.size() == 2) {
      return response = "C'mon, send something original!";
    }

    // check if any of the last message's phrases have been mapped already
    for (String key : mostRecent) {
      if (allPhraseMap.containsKey(key)) {
        randResponse = generator.nextInt(allPhraseMap.get(key).size()); // if so, choose a random phrase from the mapped responses
        return response = adjustMessages(allPhraseMap.get(key).get(randResponse), curUser, options); // return the random phrase with any possible adjustments made
      }
    }

    // otherwise choose a phrase randomly from the listed responses
    randResponse = generator.nextInt(allPhraseList.size());
    // keep refreshing the return phrase until it's not a parrot of the most recent message or response
    while (mostRecent.contains(allPhraseList.get(randResponse)) ||
    response.equals(allPhraseList.get(randResponse))) {
      randResponse = generator.nextInt(allPhraseList.size());
    }

    return response = adjustMessages(allPhraseList.get(randResponse), curUser, options);
  }

  private Set<String> splitMessage(String body) {
    Set<String> sentences = new HashSet<String>();
    for (String sentence : body.split("(?<=[!\\?\\.])")) { // use a regex to split the user message by punctuation
      if (sentence.trim().length() > 1) { // avoid phrases of length 1, which are likely meaningless
        sentences.add(sentence.trim()); // store the trimmed sentences in a set
      }
    }

    for (String trimmed : sentences) {
      // to avoid semi-duplicates, check whether the new phrase, with any punctuation, has already been added
      if (!(allPhraseSet.contains(trimmed + ".") || allPhraseSet.contains(trimmed + "!") ||
      allPhraseSet.contains(trimmed + "?"))) {
        if (allPhraseSet.add(trimmed)) { // this will check for verbatim duplicates on its own and add phrases accordingly
          allPhraseList.add(trimmed); // also add the phrase to the list, which will be used for random access
          if (response.length() >= 1) {
            allPhraseMap.get(response).add(trimmed); // finally, also map this phrase to the last response
          }
        }
      }
    }

    return sentences;
  }

  private String adjustMessages(String phrase, String curUser, int options) {
    // replace instances of the user mentioning the bot with the user's name
    if ((options & SWITCH_NAME) == SWITCH_NAME) {
      phrase = phrase.replaceAll("bot", curUser).replaceAll("Bot", curUser);
    }

    // change a "you" to a "you too" in response
    if ((options & SWITCH_YOUTOO) == SWITCH_YOUTOO) {
      phrase = new StringBuilder(phrase).insert(phrase.lastIndexOf("you") + 3, " too").toString();
    }

    return phrase;
  }

  private boolean canAddYouToo(String phrase) {
    int youIndex = phrase.lastIndexOf("you");
    // return whether the sentence structure makes sense for a "too" to be added
    return (youIndex > 0 && youIndex >= phrase.length() - 4 && phrase.charAt(phrase.length() - 1) != '?');
  }

  private boolean mostRecentContainsSubstring(String substr, Set<String> mostRecent) {
    String regex;
    switch (substr) { // use a switch to determine which regex should be used
      case "How are you?": regex = ".*[H|h]ow a{0,1}re{0,1} y{0,1}o{0,1}u\\?*.*";
        break;
      case "What's up?": regex = ".*[W|w]hat('{0,1}s| is) up\\?*.*";
        break;
      case "What are you doing?": regex = ".*[W|w]hat(cha| a{0,1}re{0,1} y{0,1}o{0,1}u) doing{0,1}\\?*.*";
        break;
      default: regex = ".*" + substr + ".*";
        break;
      }

      for (String phrase : mostRecent) { // check if any of the phrases in the last message match the regex
        if (phrase.matches(regex)) {
          return true; // return true on a match
        }
      }
      return false; // return false if no match gets made
  }
}
