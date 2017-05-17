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

  private static final Set<String> userPhraseSet = new HashSet<String>();

  private static final List<String> userPhraseList = new ArrayList<String>();

  public static final HashMap<String, ArrayList<String>> userPhraseMap = new HashMap<String, ArrayList<String>>();

  public static final HashMap<String, String> responseMap = new HashMap<String, String>();

  private static final Random generator = new Random();

  private final int SWITCH_NAME = 1; // Variable used for a bitmask

  private final int SWITCH_YOUTOO = 2; // Variable used for a bitmask

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

        // Automatically create the bot user and the conversation with the bot once a user signs in
        if (clientContext.user.lookup(BOT_NAME) == null) // Check whether the bot/convo already exist
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

            respondAsBot(body, true);
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

  public String doOneTestCommand(Scanner lineScanner) {
    final Scanner tokenScanner = new Scanner(lineScanner.nextLine());
    if (!tokenScanner.hasNext()) {
      return "";
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

        // Automatically create the bot user and the conversation with the bot once a user signs in
        if (clientContext.user.lookup(BOT_NAME) == null) // Check whether the bot and convo already exist
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

            respondAsBot(body, false);
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
    return response;
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

  private void respondAsBot(String body, boolean print) {
    // Check if the current conversation is with the bot and respond accordingly
    final ConversationSummary current = clientContext.conversation.getCurrent();
    if (!current.title.equals(BOT_CONVO)) {
      return;
    }

    final User bot = clientContext.user.lookup(BOT_NAME);

    if (responseMap.size() == 0)
      initializeResponseMap();

    response = chooseBotMessage(body);
    if (print) // Print out the bot's response if this isn't the test
      System.out.println(response);

    // Create a pair for the map if it's not already there
    addNewUserPair(response);

    clientContext.message.addMessage(bot.id,
    clientContext.conversation.getCurrentId(),
    response); // Send the String returned by the method back as the bot response
  }

  private void initializeResponseMap() {
      String curUser = clientContext.user.getCurrent().name;
      responseMap.put("How are you?", "I'm good, " + curUser + ", thanks. How are you?");
      responseMap.put("What's up?", "Just hanging out! What about you?");
      responseMap.put("What are you doing?", "Just hanging out! What about you?");
      responseMap.put("It's nice to meet you!", "It's nice to meet you too, " + curUser);
      responseMap.put("It's great to meet you!", "It's great to meet you too, " + curUser);
      responseMap.put("It's a pleasure to meet you!", "It's a pleasure to meet you too, " + curUser);
      responseMap.put("Good-bye", "Bye " + curUser + "!");
      responseMap.put("See ya", "Bye " + curUser + "!");
      responseMap.put("I'm bored", "Yeah, me too");
      responseMap.put("What?", "What do you mean, 'What?'");
      responseMap.put("How's life?", "Oh, you know. Same old");
      responseMap.put("What are your hobbies?", "Sleeping, reading, chatting with cool kids like you, etc.");
      responseMap.put("What do you like to do?", "Sleeping, reading, chatting with cool kids like you, etc.");
      responseMap.put("You said that already", "Whoops, sorry - I'm forgetful like that!");
      responseMap.put("You already told me", "Whoops, sorry - I'm forgetful like that!");
      responseMap.put("What are you talking about?", "Huh? What are YOU talking about?");
      responseMap.put("That was random", "Yeah, sorry...I'm kind of random sometimes");
  }

  private String chooseBotMessage(String body) {
    int randResponse;
    int options = 1; // Initially set the bitmask to always switch names

    // Split the user's message by sentence/phrase and store the results
    Set<String> mostRecent = splitMessage(body);
    String curUser = clientContext.user.getCurrent().name;

    // Send a generic response if this is the beginning of the conversation
    if (clientContext.conversation.currentMessageCount() <= 1) {
      return response = "Hey " + curUser + "! I'm the Bot :P";
    }
    else if (response.equals("I'm good, " + curUser + ", thanks. How are you?")) {
      return response = "Cool, that's good to hear";
    }
    // Check for several standard messages
    else if (mostRecentContainsSubstring(mostRecent)) {
      return response;
    }
    // Respond with a "you too" if possible
    else if (userPhraseList.get(userPhraseList.size() - 1).equals(response) == false &&
             canAddYouToo(userPhraseList.get(userPhraseList.size() - 1))) {
      options |= SWITCH_YOUTOO;
      return response = adjustMessages(userPhraseList.get(userPhraseList.size() - 1), curUser, options);
    }
    // Let the user know that the bot doesn't have enough phrases to pick from
    else if (userPhraseList.size() <= 1 ||
            clientContext.conversation.currentMessageCount() > 4 && userPhraseList.size() == 2) {
      return response = "C'mon, send something original!";
    }
    else {
      // Check if any of the last message's phrases have been mapped already
      for (String key : mostRecent) {
        if (userPhraseMap.containsKey(key)) {
          // If so, choose and return a random phrase from the mapped responses with adjustments made
          randResponse = generator.nextInt(userPhraseMap.get(key).size());
          return response = adjustMessages(userPhraseMap.get(key).get(randResponse), curUser, options);
        }
      }

      // Otherwise choose a phrase randomly from the listed responses
      randResponse = generator.nextInt(userPhraseList.size());
      // Keep refreshing the return phrase until it's not a parrot of the most recent message or response
      while (mostRecent.contains(userPhraseList.get(randResponse)) ||
      response.equals(userPhraseList.get(randResponse))) {
        randResponse = generator.nextInt(userPhraseList.size());
      }
      return response = adjustMessages(userPhraseList.get(randResponse), curUser, options);
    }
  }

  private void addNewUserPair(String response) {
      if (response.length() >= 1 && !userPhraseMap.containsKey(response)) {
        ArrayList<String> blankList = new ArrayList<String>();
        userPhraseMap.put(response, blankList);
    }
  }

  private Set<String> splitMessage(String body) {
    Set<String> sentences = new HashSet<String>();
    for (String sentence : body.split("(?<=[!\\?\\.])")) { // Split the user message by punctuation
      if (sentence.trim().length() > 1) { // Avoid phrases of length 1, which are likely meaningless
        sentences.add(sentence.trim()); // Store the trimmed sentences in a set
      }
    }

    for (String trimmed : sentences) {
      // Don't add anything that matches a general greeting
      if (!((getLevenshteinDistance(trimmed, "How are you?")) <= trimmed.length() / 2.0
        || trimmed.contains("hello") || trimmed.contains("Hello")))  {
        if (!(userPhraseSet.contains(trimmed + ".") || userPhraseSet.contains(trimmed + "!") ||
        userPhraseSet.contains(trimmed + "?"))) {
          // This will check for verbatim duplicates on its own and add phrases accordingly
          if (userPhraseSet.add(trimmed)) {
            // Also add this phrase to the list, which will be used for random access
            userPhraseList.add(trimmed);
          }
        }
        // Finally, also map this phrase to the last response
        if (response.length() >= 1) {
          userPhraseMap.get(response).add(trimmed);
        }
      }
    }
    return sentences;
  }

  private String adjustMessages(String phrase, String curUser, int options) {
    /* Replace instances of the user mentioning the bot with the user's name,
       as well as instances of the user mentioning himself/herself with the bot's name */
    if ((options & SWITCH_NAME) == SWITCH_NAME) {
      phrase = phrase.replaceAll(curUser, "TEMPSTRING");
      phrase = phrase.replaceAll(BOT_NAME.toLowerCase(), curUser).replaceAll(BOT_NAME, curUser);
      phrase = phrase.replaceAll("TEMPSTRING", "Bot");
    }

    // Change a "you" to a "you too" in response
    if ((options & SWITCH_YOUTOO) == SWITCH_YOUTOO) {
      phrase = new StringBuilder(phrase).insert(phrase.lastIndexOf("you") + 3, " too").toString();
    }

    return phrase;
  }

  private boolean canAddYouToo(String phrase) {
    int youIndex = phrase.lastIndexOf("you");
    // Return whether the sentence structure makes sense for a "too" to be added
    return (youIndex > 0 && youIndex >= phrase.length() - 4 && phrase.charAt(phrase.length() - 1) != '?');
  }

  private boolean mostRecentContainsSubstring(Set<String> mostRecent) {
    int distance;
    double bestPercent = 1;
    String bestResponse = "";

    for (String key : responseMap.keySet()) {
      for (String phrase : mostRecent) {
        // Check for a match between user phrase and stored phrases using edit distance
        if ((distance = getLevenshteinDistance(key, phrase)) <= key.length() / 2.0) {
          // If a match is found, record the percent match and response and move on
          if ((double)distance / key.length() < bestPercent) {
            bestPercent = (double)distance / key.length();
            bestResponse = responseMap.get(key);
          }
        }
      }
    }
    if (bestResponse.length() == 0) // Return false if no match gets made
      return false;

    response = bestResponse;
    return true;
  }

  // Verbatim source code taken from the Apache Commons StringUtils class
  private static int getLevenshteinDistance(CharSequence s, CharSequence t) {
      if (s == null || t == null) {
          throw new IllegalArgumentException("Strings must not be null");
      }
      int n = s.length(); // length of s
      int m = t.length(); // length of t

      if (n == 0) {
          return m;
      } else if (m == 0) {
          return n;
      }

      if (n > m) {
          // swap the input strings to consume less memory
          final CharSequence tmp = s;
          s = t;
          t = tmp;
          n = m;
          m = t.length();
      }

      int p[] = new int[n + 1]; //'previous' cost array, horizontally
      int d[] = new int[n + 1]; // cost array, horizontally
      int _d[]; //placeholder to assist in swapping p and d

      // indexes into strings s and t
      int i; // iterates through s
      int j; // iterates through t

      char t_j; // jth character of t

      int cost; // cost

      for (i = 0; i <= n; i++) {
          p[i] = i;
      }

      for (j = 1; j <= m; j++) {
          t_j = t.charAt(j - 1);
          d[0] = j;

          for (i = 1; i <= n; i++) {
              cost = s.charAt(i - 1) == t_j ? 0 : 1;
              // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
              d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
          }

          // copy current distance counts to 'previous row' distance counts
          _d = p;
          p = d;
          d = _d;
      }

      // our last action in the above loop was to switch d and p, so p now
      // actually has the most recent cost counts
      return p[n];
  }
}
