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

package codeu.chat.common;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import java.lang.*;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import java.util.Scanner;

import codeu.chat.client.ClientContext;
import codeu.chat.client.Controller;
import codeu.chat.client.View;
import codeu.chat.common.ConversationSummary;
import codeu.chat.util.Logger;

import codeu.chat.common.User;
import codeu.chat.client.commandline.Chat;
import codeu.chat.util.RemoteAddress;
import codeu.chat.util.connections.ClientConnectionSource;
import codeu.chat.util.connections.ConnectionSource;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public final class BotTest {

  static final RemoteAddress address = RemoteAddress.parse("localhost@2007");

  static final ConnectionSource source = new ClientConnectionSource(address.host, address.port);
  static final Controller controller = new Controller(source);
  static final View view = new View(source);

  private static Chat chat;
  private static Scanner input;

  private static final String cmd = "m-add ";
  private static final String user = "bart";
  private static String response;
  private static String oldResponse;

  private static final class TestBot {

    public TestBot() {}
  }

  @BeforeClass
  public static void doBeforeClass() {
    // Input the necessary set-up commands
    chat = new Chat(controller, view);
    input = new Scanner("u-add " + user);
    chat.doOneTestCommand(input);
    input = new Scanner("sign-in " + user);
    chat.doOneTestCommand(input);
    input = new Scanner("c-select\n1");
    chat.doOneTestCommand(input);
  }

  @Test
  public void testScriptedResponses() {
    input = new Scanner(cmd + "hey");
    response = chat.doOneTestCommand(input);
    assertTrue(response.equals("Hey " + user + "! I'm the Bot :P"));

    // Store and shuffle the keys to test them in a random order
    List<String> keyList = new ArrayList<String>(Chat.responseMap.keySet());
    Collections.shuffle(keyList);

    input = new Scanner(cmd + "How are you?");
    response = chat.doOneTestCommand(input);
    assertTrue(response.equals(Chat.responseMap.get("How are you?")));

    input = new Scanner(cmd + "I'm good, thanks!");
    response = chat.doOneTestCommand(input);
    assertTrue(response.equals("Cool, that's good to hear"));

    // Test whether each key leads to its given value being the bot's response
    for (String key : keyList) {
      if (key.equals("How are you?")) // Skip this key, since it was checked earlier
        continue;

      input = new Scanner(cmd + key);
      response = chat.doOneTestCommand(input);
      assertTrue(response.equals(Chat.responseMap.get(key)));
    }

    System.out.println("TESTING FOR SCRIPTED RESPONSES PASSES");
  }

  @Test
  public void testAdjustedResponses() {
    input = new Scanner(cmd + "I like talking to you");
    response = chat.doOneTestCommand(input);
    assertTrue(response.equals("I like talking to you too"));
    oldResponse = response;

    input = new Scanner(cmd + "Hey bot, you're really cool");
    response = chat.doOneTestCommand(input);
    assertFalse(response.contains("bot"));
    assertFalse(response.equals(oldResponse));
    oldResponse = response;

    input = new Scanner(cmd + "psst, Bot, I have something to tell you");
    response = chat.doOneTestCommand(input);
    assertFalse(response.contains("bot"));
    assertTrue(response.equals("psst, " + user + ", I have something to tell you too"));
    oldResponse = response;

    System.out.println("TESTING FOR ADJUSTED RESPONSES PASSES");
  }

  @Test
  public void testRandomResponses() {
    int index;
    List<String> testStrings = Arrays.asList("foobar",
                                             "bot you're the best",
                                             "this is a test string",
                                             "the Bot is cool",
                                             "I don't know what to say",
                                             "My name is " + user,
                                             "here is another test string",
                                             "barfoo",
                                             user,
                                             "testing",
                                             "I'll see ya next time, bot");
    Random randomGenerator = new Random();

    // Because responses are now random, run a lot of tests to get a good representation
    for (int i = 0; i < 100; i++) {
      index = randomGenerator.nextInt(testStrings.size());
      input = new Scanner(cmd + testStrings.get(index));
      response = chat.doOneTestCommand(input);

      // Every "bot" should now be the user's name
      if (response.contains("you're the best"))
        assertTrue(response.equals(user + " you're the best"));
      if (response.contains("is cool"))
        assertTrue(response.equals("the " + user + " is cool"));

      // The username should be replaced with the bot's name
      assertFalse(response.equals("My name is " + user));

      // The same response should never be used twice in a row, unless it's a mapped response
      if (Chat.userPhraseMap.get(testStrings.get(index)) == null)
        assertFalse(response.equals(oldResponse));

      oldResponse = response;
    }

    System.out.println("TESTING FOR RANDOM RESPONSES PASSES");
  }
}
