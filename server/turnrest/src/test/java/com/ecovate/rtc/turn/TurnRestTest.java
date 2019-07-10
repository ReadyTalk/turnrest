package com.ecovate.rtc.turn;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyPair;
import java.util.Base64;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.client.http.HTTPClient;
import org.threadly.litesockets.client.http.HTTPClient.HTTPResponseData;
import org.threadly.litesockets.protocols.http.request.ClientHTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.utils.PortUtils;
import org.threadly.test.concurrent.TestCondition;
import org.threadly.util.StringUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class TurnRestTest {

  KeyPair goodKeyPair = null;
  KeyPair badKeyPair = null;  
  String userName = null;
  String password = null;
  PriorityScheduler ps = null;
  SocketExecuter se = null;
  JWTUtils ju = null;
  HTTPClient hc = null;

  @Before
  public void start() {
    goodKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
    ps = new PriorityScheduler(2);
    se = new ThreadedSocketExecuter(ps);
    se.startIfNotStarted();
    badKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
    userName = StringUtils.makeRandomString(25);
    password = StringUtils.makeRandomString(25);
    ju = new JWTUtils(ps);
    ju.addStaticKey(goodKeyPair.getPublic());
    hc = new HTTPClient();
    hc.startIfNotStarted();
  }

  @After
  public void end() {
    ju.reset();
    Utils.reset();
    se.stopIfRunning();
    ps.shutdownNow();
    hc.stopIfRunning();
  }


  @Test
  public void testConfigReloading() throws IOException, HTTPParsingException, InterruptedException {
    int port = PortUtils.findTCPPort();
    InetSocketAddress isa = new InetSocketAddress("127.0.0.1", port);
    TurnRestConfig trc = new TurnRestConfig("12312", 
        false, 
        new String[] {"turn:turn.test.com"}, 
        new String[] {"stun:stun.test.com"}, 
        null, 
        new String[] {Base64.getEncoder().encodeToString(goodKeyPair.getPublic().getEncoded())}, 
        null, 
        null, 
        15000L, 
        userName, 
        password, 
        "*");
    File testFile = File.createTempFile("testFile1", "testing");

    RandomAccessFile raf = new RandomAccessFile(testFile, "rw");
    raf.write(trc.toString().getBytes());
    raf.close();
    System.out.println(testFile.getAbsolutePath());

    TurnRest tr = new TurnRest(ps, se, isa, isa, testFile, 20);
    tr.startIfNotStarted();
    String jws = Jwts.builder().setSubject("Bob").signWith(goodKeyPair.getPrivate()).compact();



    ClientHTTPRequest chr = new HTTPRequestBuilder()
        .setURL(new URL("http://127.0.0.1:"+port))
        .buildClientHTTPRequest();
    System.out.println(chr.getHTTPRequest());
    HTTPResponseData hr = hc.request(chr);
    assertEquals(HTTPResponseCode.NotFound, hr.getResponseCode());
    System.out.println(hr);


    chr = new HTTPRequestBuilder()
        .setURL(new URL("http://127.0.0.1:"+port))
        .setPath("/turn")
        .buildClientHTTPRequest();
    hr = hc.request(chr);
    assertEquals(HTTPResponseCode.Unauthorized, hr.getResponseCode());


    chr = new HTTPRequestBuilder()
        .setURL(new URL("http://127.0.0.1:"+port))
        .setPath("/turn")
        .setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer "+jws)
        .buildClientHTTPRequest();
    hr = hc.request(chr);
    assertEquals(HTTPResponseCode.OK, hr.getResponseCode());
    String body = hr.getBodyAsString();
    System.out.println(body);
    TurnRestResponse trr = Utils.GSON.fromJson(body, TurnRestResponse.class);
    assertEquals(userName, trr.getUsername());
    assertEquals(password, trr.getPassword());
    System.out.println("-------------------------");
    trc = new TurnRestConfig("12312", 
        false, 
        new String[] {"turn:turn.test.com"}, 
        new String[] {"stun:stun.test.com"}, 
        null, 
        new String[] {Base64.getEncoder().encodeToString(badKeyPair.getPublic().getEncoded())}, 
        null, 
        null, 
        15000L, 
        userName, 
        password, 
        "*");
    final long mt = testFile.lastModified();

    Thread.sleep(300);
    new TestCondition(){
      @Override
      public boolean get() {
        TurnRestConfig trc = new TurnRestConfig("12312", 
            false, 
            new String[] {"turn:turn.test.com"}, 
            new String[] {"stun:stun.test.com"}, 
            null, 
            new String[] {Base64.getEncoder().encodeToString(badKeyPair.getPublic().getEncoded())}, 
            null, 
            null, 
            15000L, 
            userName, 
            password, 
            "*");
        try {
          RandomAccessFile raf = new RandomAccessFile(testFile, "rw");
          raf.write(trc.toString().getBytes());
          raf.close();
          return testFile.lastModified() != mt;
        } catch(Exception e) {
          return false;
        }
      }
    }.blockTillTrue(5000);
    Thread.sleep(300);
    System.out.println("-------------------------");
    tr.getJWTUtils().clearJWTCache();
    
    chr = new HTTPRequestBuilder()
        .setURL(new URL("http://127.0.0.1:"+port))
        .setPath("/turn")
        .setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer "+jws)
        .buildClientHTTPRequest();
    hr = hc.request(chr);
    assertEquals(HTTPResponseCode.Unauthorized, hr.getResponseCode());

    jws = Jwts.builder().setSubject("Bob").signWith(badKeyPair.getPrivate()).compact();
    chr = new HTTPRequestBuilder()
        .setURL(new URL("http://127.0.0.1:"+port))
        .setPath("/turn")
        .setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer "+jws)
        .buildClientHTTPRequest();
    hr = hc.request(chr);
    assertEquals(HTTPResponseCode.OK, hr.getResponseCode());
    body = hr.getBodyAsString();
    System.out.println(body);
    trr = Utils.GSON.fromJson(body, TurnRestResponse.class);
    assertEquals(userName, trr.getUsername());
    assertEquals(password, trr.getPassword());
    tr.stopIfRunning();
    testFile.delete();
  }
}
