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

import org.jsoup.Jsoup;
import org.jsoup.helper.Validate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

// Chat - top-level client application.
public final class Chat {

  private final static Logger.Log LOG = Logger.newLog(Chat.class);

  private static final String PROMPT = ">>";

  private static final String BOT_NAME = "Bot";
  private static final String BOT_CONVO = "Convo with Bot";

  private static String response = "";
  private static final String HOWRU_RESP = "Cool, that's good to hear!";
  private static final String NO_MSGS = "C'mon, send something original!";

  private static final Set<String> userPhraseSet = new HashSet<String>();
  private static final List<String> userPhraseList = new ArrayList<String>();
  public static final HashMap<String, ArrayList<String>> userPhraseMap = new HashMap<String, ArrayList<String>>();

  public static final HashMap<String, String> responseMap = new HashMap<String, String>();

  public static final HashMap<String, ArrayList<String>> scriptMap = new HashMap<String, ArrayList<String>>();

  private static final Random generator = new Random();

  // Variables used for adjusting responses
  private boolean SWITCH_NAME = true;
  private boolean SWITCH_YOUTOO = false;

  private static final String FORMAT_STR = "https://www.google.com/search?q=\"%s\"+movie+script";
  private static final int MIN_LENGTH = 8;
  private static final int MAX_LINES = 4;
  private static String MATCHED_PHRASE = "";

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

    response = capitalizeFirstLetter(chooseBotMessage(body));
    if (print) // Print out the bot's response if this isn't the test
      System.out.println(response);

    // Create a pair for the map if it's not already there
    addNewUserPair(response);

    // Send the String returned by the method back as the bot response
    clientContext.message.addMessage(bot.id,
                                     clientContext.conversation.getCurrentId(),
                                     response);
  }

  private void initializeResponseMap() {
      String curUser = clientContext.user.getCurrent().name;
      List<String> mappedResps;
      responseMap.put("how are you?", "I'm good, " + curUser + ", thanks. How are you?");
      responseMap.put("what's up?", "Just hanging out! What about you?");
      responseMap.put("what are you doing?", "Just hanging out! What about you?");
      responseMap.put("it's nice to meet you!", "It's nice to meet you too, " + curUser);
      responseMap.put("it's great to meet you!", "It's great to meet you too, " + curUser);
      responseMap.put("it's a pleasure to meet you!", "It's a pleasure to meet you too, " + curUser);
      responseMap.put("good-bye", "Bye " + curUser + "!");
      responseMap.put("see ya", "Bye " + curUser + "!");
      responseMap.put("i'm bored", "Yeah, me too");
      responseMap.put("what?", "What do you mean, 'What?'");
      responseMap.put("how's life?", "Oh, you know. Same old");
      responseMap.put("what are your hobbies?", "Sleeping, reading, chatting with cool kids like you, etc.");
      responseMap.put("what do you like to do?", "Sleeping, reading, chatting with cool kids like you, etc.");
      responseMap.put("you said that already", "Whoops, sorry - I'm forgetful like that!");
      responseMap.put("you already told me", "Whoops, sorry - I'm forgetful like that!");
      responseMap.put("what are you talking about?", "Huh? What are YOU talking about?");
      responseMap.put("that was random", "Yeah, sorry...I'm kind of random sometimes");
  }

  private String chooseBotMessage(String body) {
    int randResponse;
    // Initialize the adjustment booleans
    SWITCH_NAME = true;
    SWITCH_YOUTOO = false;

    // Split the user's message by sentence/phrase and store the results
    Set<String> mostRecent = splitMessage(body);
    String curUser = clientContext.user.getCurrent().name;

    // Send a generic response if this is the beginning of the conversation
    if (clientContext.conversation.currentMessageCount() <= 1) {
      return response = String.format("Hey %s! I'm the Bot :P", curUser);
    }
    else if (response.equals(String.format("I'm good, %s, thanks. How are you?", curUser))) {
      return response = HOWRU_RESP;
    }
    // Check for several standard messages
    else if (mostRecentContainsSubstring(mostRecent)) {
      return response;
    }
    else if (checkPhrasesForOnlineScript(mostRecent)) {
      /* Return a random response from the script list that one of the user's
         phrases is mapped to */
      randResponse = generator.nextInt(scriptMap.get(MATCHED_PHRASE).size());
      return response = scriptMap.get(MATCHED_PHRASE).get(randResponse);
    }
    // Respond with a "you too" if possible
    else if (userPhraseList.get(userPhraseList.size() - 1).equals(response) == false &&
             canAddYouToo(userPhraseList.get(userPhraseList.size() - 1))) {
      SWITCH_YOUTOO = true;
      return response = adjustMessages(userPhraseList.get(userPhraseList.size() - 1), curUser);
    }
    // Let the user know that the bot doesn't have enough phrases to pick from
    else if (userPhraseList.size() <= 1 ||
            clientContext.conversation.currentMessageCount() > 4 && userPhraseList.size() == 2) {
      return response = NO_MSGS;
    }
    else {
      // Check if any of the last message's phrases have been mapped already
      for (String key : mostRecent) {
        if (userPhraseMap.containsKey(key)) {
          // If so, choose and return a random phrase from the mapped responses with adjustments made
          randResponse = generator.nextInt(userPhraseMap.get(key).size());
          return response = adjustMessages(userPhraseMap.get(key).get(randResponse), curUser);
        }
      }

      // Otherwise choose a phrase randomly from the listed responses
      randResponse = generator.nextInt(userPhraseList.size());
      /* Keep refreshing the return phrase until it's not a parrot of the
         most recent message or response */
      while (mostRecent.contains(userPhraseList.get(randResponse)) ||
             response.toLowerCase().equals(userPhraseList.get(randResponse))) {
        randResponse = generator.nextInt(userPhraseList.size());
      }
      return response = adjustMessages(userPhraseList.get(randResponse), curUser);
    }
  }

  // Instantiate an empty list as the value for the given response in the user map.
  private void addNewUserPair(String response) {
    if (response.length() >= 1 && !userPhraseMap.containsKey(response)) {
      ArrayList<String> blankList = new ArrayList<String>();
      userPhraseMap.put(response, blankList);
    }
  }

  // Instantiate an empty list as the value for the given phrase in the script map.
  private void addNewScriptPair(String phrase) {
    if (!scriptMap.containsKey(phrase)) {
      ArrayList<String> blankList = new ArrayList<String>();
      scriptMap.put(phrase, blankList);
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
      if (!((getLevenshteinDistance(trimmed, "How are you?")) <= "How are you?".length() / 3.0 ||
             trimmed.contains("hello") || trimmed.contains("Hello")))  {
        if (!(userPhraseSet.contains(trimmed + ".") || userPhraseSet.contains(trimmed + "!") ||
        userPhraseSet.contains(trimmed + "?"))) {
          // This will check for verbatim duplicates on its own and add phrases accordingly
          if (userPhraseSet.add(trimmed)) {
            // Also add this phrase to the list, which will be used for random access
            userPhraseList.add(trimmed);
          }
        }
        // Finally, also map this phrase to the previous response, which hasn't been updated yet.
        if (response.length() >= 1) {
          userPhraseMap.get(response).add(trimmed);
        }
      }
    }
    return sentences;
  }

  private String adjustMessages(String phrase, String curUser) {
    /* Replace instances of the user mentioning the bot with the user's name,
       as well as instances of the user mentioning himself/herself with the bot's name */
    if (SWITCH_NAME) {
      phrase = phrase.replaceAll(curUser, "TEMPSTRING");
      phrase = phrase.replaceAll(BOT_NAME.toLowerCase(), curUser).replaceAll(BOT_NAME, curUser);
      phrase = phrase.replaceAll("TEMPSTRING", "Bot");
    }

    // Change a "you" to a "you too" in response
    if (SWITCH_YOUTOO) {
      phrase = new StringBuilder(phrase).insert(phrase.lastIndexOf("you") + 3, " too").toString();
    }
    return phrase;
  }

  private boolean canAddYouToo(String phrase) {
    int youIndex = phrase.lastIndexOf("you");
    /* Return whether the sentence structure makes sense for a "too" to be added.
       This is determined by checking whether "you" comes near the end of the phrase,
       but is not part of a question. */
    return (youIndex > 0 && youIndex >= phrase.length() - 4 && phrase.charAt(phrase.length() - 1) != '?');
  }

  private boolean mostRecentContainsSubstring(Set<String> mostRecent) {
    int distance;
    int randResponse;
    double bestPercent = 1;
    String bestResponse = "";

    // Check the prewritten responses
    for (String key : responseMap.keySet()) {
      for (String phrase : mostRecent) {
        // Check for a match between user phrase and stored phrases using edit distance
        if ((!phraseReferencesSelf(phrase) && (distance =
              getLevenshteinDistance(key, phrase)) <= phrase.length() / 3.0)) {
          // If a match is found, record the percent match and response and move on
          if ((double)distance / key.length() < bestPercent) {
            bestPercent = (double)distance / key.length();
            bestResponse = responseMap.get(key);
          }
        }
        else if (phrase.toLowerCase().contains(key) &&
                 phrase.length() <= key.length() * 1.3) {
          bestResponse = responseMap.get(key);
        }
      }
    }

    // Check the responses gained from online movie scripts
    for (String key : scriptMap.keySet()) {
      for (String phrase : mostRecent) {
        // Check for a match between user phrase and stored phrases using edit distance
        if ((!phraseReferencesSelf(phrase) && (distance =
              getLevenshteinDistance(key, phrase)) <= phrase.length() / 3.0)) {
          // If a match is found, record the percent match and response and move on
          if ((double)distance / key.length() < bestPercent) {
            bestPercent = (double)distance / key.length();
            randResponse = generator.nextInt(scriptMap.get(key).size());
            bestResponse = scriptMap.get(key).get(randResponse);
          }
        }
        else if (phrase.toLowerCase().contains(key)) {
          randResponse = generator.nextInt(scriptMap.get(key).size());
          bestResponse = scriptMap.get(key).get(randResponse);
        }
      }
    }

    if (bestResponse.length() == 0) // Return false if no match gets made
      return false;

    response = bestResponse;
    return true;
  }

  private static boolean phraseReferencesSelf(String phrase) {
    phrase = phrase.toLowerCase();
    if (phrase.substring(0, 2).equals("i ") ||
        phrase.contains(" i ")) {
      return true;
    }
    return false;
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

  private boolean checkPhrasesForOnlineScript(Set<String> mostRecent) {
    Document doc;
    String url;
    boolean found = false;

    // Test each phrase sent to see whether a script containing it can be found
    for (String phrase : mostRecent) {
      phrase = phrase.toLowerCase().trim();
      if (phrase.length() < MIN_LENGTH) { // Skip the shorter, more generic phrases
        continue;
      }
      // Construct the Google search query for this phrase
      url = String.format(FORMAT_STR, phrase.replace(' ', '+'));

      List<String> links = findScript(url); // Look for scripts in the search results
      if (links.size() == 0) { 
        continue;
      }

      /* If any scripts were found, iterate through all of them
         to see if a suitable response can be found */
      MATCHED_PHRASE = phrase;
      addNewScriptPair(MATCHED_PHRASE);
      for (String link : links) {
        if (link.contains("script-o-rama")) {
          if (parseScript(link, phrase, false)) {
            found = true;
          }
        }
        else {
          if (parseScript(link, phrase, true)) {
            found = true;
          }
        }
      }
    }
    return found; // Return whether any scripts could be found for any phrase
  }

  private List<String> findScript(String url) {
    List<String> elemLinks = new ArrayList<String>();
    try {
      Document doc = Jsoup.connect(url).get(); // Make the request
      String elemLink, elemText;

      // Parse the search results
      Elements links = doc.select("a[href]");
      for (Element link : links) {
        elemLink = link.attr("href");
        elemText = link.text();

        /* Check if any scripts for a movie in this Google search were found.
           If so, add them to the links list */
        if ((elemLink.contains("script-o-rama") || elemLink.contains("springfieldspringfield"))
          && !(elemText.equals("Cached") || elemText.equals("Similar"))) {
          elemLinks.add(elemLink);
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return elemLinks; // Return an empty string to indicate failure
  }

  private boolean parseScript(String link, String phrase, boolean springfield) {
    String[] script;

    try {
      Document doc = Jsoup.connect(link).get();

      /* If the script was retrieved from the Springfield website, the lines
         must be split up using the <br> tag instead of new line characters */
      if (springfield) {
        String temp = Jsoup.parse(doc.html().replaceAll("(?i)<br[^>]*>", "br2n")).text();
        script = mergeScriptSentences(temp.split("br2n"));
      }
      else {
        script = mergeScriptSentences(doc.body().text().split("\n"));
      }

      /* Search for a line containing the phrase. Once one is found,
         determine the best response and return accordingly. In some
         cases, this will mean continuing to search for a later match */
      for (int lineNum = 0; lineNum < script.length; lineNum++) {
        script[lineNum] = script[lineNum].trim();
        for (String sentence : script[lineNum].split("(?<=[!\\?\\.])")) {
          if (getLevenshteinDistance(sentence.toLowerCase(), phrase)
              <= phrase.length() / 3.0) {
            if (findNextScriptResponse(lineNum, phrase, script)) {
              return true;
            }
          }
        }
      }
    }
    catch (IOException e) {
          e.printStackTrace();
    }
    return false; // Return false if no line containing the phrase was found
  }

  private String[] mergeScriptSentences(String[] script) {
    List<String> tempScript = new ArrayList<String>();
    int lineNum = 0;
    String mergedLine;
    char last = ' ';

    while (lineNum < script.length) {
      mergedLine = "";
      script[lineNum] = script[lineNum].trim();

      /* Loop until a line ending in punctuation comes up, all the while
         combining all the lines since the last punct. into one string */
      last = getLastChar(script[lineNum]);
      while (lineNum < script.length && last != '.'
             && last != '?' && last != '!') {
        mergedLine += script[lineNum];
        mergedLine += " ";

        lineNum = advanceToNextNonBlankLine(lineNum, script);

        if (lineNum < script.length) {
          script[lineNum] = script[lineNum].trim();
          last = getLastChar(script[lineNum]);
        }
      }

      if (lineNum < script.length) {
        mergedLine += script[lineNum];
        lineNum++;
      }
      tempScript.add(adjustScriptLine(mergedLine.trim()));
    }

    String[] newScript = new String[tempScript.size()];
    newScript = tempScript.toArray(newScript);
    return newScript;
  }

  private char getLastChar(String line) {
    char last;
    try {
      last = line.charAt(line.length() - 1);
    }
    catch (StringIndexOutOfBoundsException exception) {
      last = ' ';
    }
    return last;
  }

  private String adjustScriptLine(String phrase) {
    // Check if this script has character names, followed by a colon, for each line
    if (phrase.indexOf(":") > 0 && phrase.indexOf(":") < phrase.indexOf(" ")) {
      phrase = phrase.substring(phrase.indexOf(":") + 1).trim();
    }

    // Check if this script has character names in brackets for each line
    if (phrase.indexOf("]") > 0 && phrase.indexOf("]") < phrase.indexOf(" ")) {
      phrase = phrase.substring(phrase.indexOf("]") + 1).trim();
    }

    // Check if this script has begins lines with dashes
    if (phrase.indexOf("-") >= 0 && phrase.indexOf("-") < phrase.indexOf(" ")) {
      phrase = phrase.substring(phrase.indexOf("-") + 1).trim();
    }

    return phrase;
  }

  private boolean findNextScriptResponse(int lineNum, String phrase, String[] script) {
    /* Check if a line from a different character (i.e. an actual response)
       can be found. If not, just respond with the very next line of dialogue */
    if (canFindNewCharacter(lineNum, script)) {
      return true;
    }

    /* Next, check if the line containing this phrase contains more wording. If it
       does, use the next sentence as the response */
    if (lineContainsMoreSentences(script[lineNum], phrase)) {
      return true;
    }

    // Advance to the next non-blank line, indicating the next piece of dialogue
    lineNum = advanceToNextNonBlankLine(lineNum, script);

    // Ensure that there was a next line of dialogue
    if (lineNum >= script.length ||
        getLevenshteinDistance(script[lineNum].trim(), phrase) < phrase.length() / 2.0) {
      return false;
    }

    /* Add this response found from an online script to the
       response map for future reference/usage */
    scriptMap.get(MATCHED_PHRASE).add(script[lineNum].trim());
    return true;
  }

  private boolean canFindNewCharacter(int lineNum, String[] script) {
    int counter = 0;

    lineNum = advanceToNextNonBlankLine(lineNum, script);
    while (lineNum < script.length && counter <= MAX_LINES) {

      /* A '-' indicates a new character's line. If one is found to
         start a line, and that line contains at least two words, add
         that phrase to the script map of responses and return. */
      if (script[lineNum].charAt(0) == '-' &&
          script[lineNum].substring(1).trim().contains(" ")) {
        scriptMap.get(MATCHED_PHRASE).add(script[lineNum].substring(1).trim());
        return true;
      }
      counter++;
      lineNum = advanceToNextNonBlankLine(lineNum, script);
    }
    // Return false if no new character's line was found soon enough
    return false;
  }

  private boolean lineContainsMoreSentences(String line, String phrase) {
    int periodIndex, questionIndex, exclaimIndex, punctIndex;
    String nextSentence;

    line = line.trim().toLowerCase();
    phrase = phrase.toLowerCase();
    int index = line.indexOf(phrase);

    /* Return false if no exact match can be made or if the match is 
       at the end of the line */
    if (index == -1 || index + phrase.length() + 1 >= line.length()) {
      return false;
    }

    nextSentence = line.substring(index + phrase.length() + 1).trim();

    /* Find the end of the next sentence by finding the index of the next
       punctuation mark */
    periodIndex =
      nextSentence.indexOf(".") != -1 ? nextSentence.indexOf(".") : Integer.MAX_VALUE;
    questionIndex =
      nextSentence.indexOf("?") != -1 ? nextSentence.indexOf("?") : Integer.MAX_VALUE;
    exclaimIndex =
      nextSentence.indexOf("!") != -1 ? nextSentence.indexOf("!") : Integer.MAX_VALUE;
    punctIndex = Math.min(periodIndex, Math.min(questionIndex, exclaimIndex));

    nextSentence = nextSentence.substring(0, punctIndex).trim();
    if (nextSentence.length() == 0) // Ensure that an empty string wasn't found
      return false;

    nextSentence = capitalizeFirstLetter(nextSentence);
    scriptMap.get(MATCHED_PHRASE).add(nextSentence);
    return true;
  }

  private int advanceToNextBlankLine(int lineNum, String[] script) {
    if (script[lineNum].trim().length() == 0) {
      lineNum++;
    }
    while (lineNum + 1 < script.length && script[lineNum].trim().length() > 0) {
      lineNum++;
      script[lineNum] = script[lineNum].trim();
    }
    return lineNum;
  }

  private int advanceToNextNonBlankLine(int lineNum, String[] script) {
    if (script[lineNum].trim().length() > 0) {
      lineNum++;
    }
    while (lineNum + 1 < script.length && script[lineNum].trim().length() == 0) {
      lineNum++;
    }
    return lineNum;
  }

  private String capitalizeFirstLetter(String s) {
    if (s.length() <= 1) {
      return s.toUpperCase();
    }

    return s.substring(0, 1).toUpperCase()
           + s.substring(1);
  }
}
