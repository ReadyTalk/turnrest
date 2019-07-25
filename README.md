# TurnRest

This is a server/client implementation of [IETF rtcweb-turn-rest](https://tools.ietf.org/html/draft-uberti-rtcweb-turn-rest-00) using JWT for the creation of the limited time turn creditials.

## TurnServer

The TurnServer is a simple rest server for providing turn creditials to clients.  These creditials expire after a given period of time which allows the hosting of a turn server that does not need a list or database of creditials to be secure.

Currently the TurnServer only supports doing JWT auth using static keys or JWK urls, but other auth methods could be added.

The TurnServer also supports prometheus stats on its admin interface.  It can use the same port as the standard interface if you dont care about the stats being public.

### Server configuration

The Server takes a json config file here is an example of it:

```json
{
  "secretKey":"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
  "allowedOrigin": "*",
  "ttl": 300, 
  "forcedUser": "username",
  "forcedPassword": "staticpassword",
  "ignoreJWT": false,
  "userClaim": "user_id",
  "requiredJWTScope": [
    "turnAllowed"
  ],
  "jwkURIS": [
    "https://jwkendpoint.com"
  ],
  "jwtPublicKeys": [
    "<BASE64 Encoded RSA key>"
  ],
  "turnURIS": [
    "turn.testserver.com"
  ],
  "stunURIS": [
    "stun.testserver.com"
  ]
}

```

Here is a breakdown of the fields:

* secretKey: This is the shared key used by this server and the turn server to generate the password hash.  This is required if you are not forcing a static password.
* allowedOrigin: This sets the allowed origin headers in the server for XSS.
* ttl: time-to-live, this is in seconds, this is how many seconds the user can use turn for.  Generally for voip/video calls the max call time can be used here, but > 1 hour is generally recommended.
* forceUser:  This forces the username set in the response, it will look like "<epochtime>:forcedUser" unless forcedPassword is also set then it will just have the username
* forcedPassword:  This is mainly for testing.  This uses a static password, bypassing all of the turn/epoch/auth time, and the user will not have the epoch in the user name if this is set.
* ignoreJWT: If true all requests for turn creditials must pass JWT auth, if false anyone can get JWT auth (for testing usually).
* userClaim: Which JWT property to use as the users name.  If none is provided forcedUser will be used, if thats also not provided it will be a random string.
* requiredJWTScope: This is a list of scopes that are used to validate the JWT can get turn creds.  NOTE: as long as one matches this will pass.
* jwkURIS: This is a list of JWK uris to check the JWT against.  We will check them in all in parallel with the jwtPublicKeys.  As long as one is valid for the JWT it will pass.
* jwtPublicKeys: A List of public Keys to check JWTs against.  This is more used for testing or backend services.
* turnURIS: a list of turnServers to tell the client about when returning the turn creditials.
* stunURIS: A list of stun Serrvers to tell the client about then returing the turn creditials.



## TurnRestClient

This is a JavaScript client for doing requests against a TurnRest server.  A description of what this is and the basics of how it works can be found at:


This client makes a request to a TurnRest server and retrives a JSON object with credentials and locations of services.  This request is cached until the TTL of the response is getting ready to expire.

### Implementation

The client library is versioned and each version is immutable.

The client only has one simple function. `getTurnInfo()`
The function takes the following arguments:

* `url`, This is a required argument, this should be the full turnRest servers API for your setup.
* `jwt`, This is optional (defaults to None)  If your setup requires a JWT to get turn credentials (a very good idea) then you should set this.
* `force`, This forces a new request regardless of if there is one cached or not.  This should only really be used for testing or if you know what you are doing.

The return of `getTurnInfo()` is a Promise that will return the JSON Object that has the credentials and the iceServers.

### Example

Include the client file like so:
```html
<html>
<head>
<script type="text/javascript" src="https://example.com/clients/turnRestClient-1.2.0.js"></script>
</head>
</html>

```

You could also include the JS libary from javascript:

```javascript
const script = document.createElement('script');
script.type = 'text/javascript';
script.src = 'https://example.com/clients/turnRestClient-1.2.0.js';
document.head.appendChild(script);
```

Then the Javascript to call the client would look like this:

```javascript
let myJWT = "some real jwt string";
let turnData = TurnService.getTurnServiceInfo("https://example.com/turn", jwt=myJWT, force=False);
console.log(turnData) //Logs the promise
turnData.then(function(data) {
  console.log(data);  //Logs the Json object
})

```

The structure of the returned JSON object looks like this:

```json

{
  "username": "1549492915:user-cHQbiYigpkmKq8En",
  "password": "JEk2+xOm19cXICxmRXED1BBiBAA=",
  "ttl": 15000,
  "iceServers": [
    {
      "urls": [
        "turns:turn.example.com?transport=tcp"
      ],
      "username": "1549492915:user-cHQbiYigpkmKq8En",
      "credential": "JEk2+xOm19cXICxmRXED1BBiBAA="
    },
    {
      "urls": [
        "stun:stun.example.com:3478"
      ]
    }
  ]
}

```

The username/password are the username/password that can be used for the turn server.

The `TTL` is how long this username/password will be valid for.

The `iceServers` section is a convenience, this is **currently** the format webRTC takes in the iceServers parameters so it is filled out for you.  Depending on how the server is configured this may or may not include a stun server and will have at least 1 turn server, though more servers are definitely possible.

### NOTES:

* the username starts with a number that is the `epoch` time in seconds that the user will expire at (~15000 seconds when the request was made).
* version 1.1.0 and 1.2.0 work the same though 1.2.0 is compatible with more browsers.
