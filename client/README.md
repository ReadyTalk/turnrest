# TurnRestClient

This is a JavaScript client for doing requests against a TurnRest server.  A description of what this is and the basics of how it works can be found at:

[IETF rtcweb-turn-rest](https://tools.ietf.org/html/draft-uberti-rtcweb-turn-rest-00)

This client makes a request to a TurnRest server and retrives a JSON object with credentials and locations of services.  This request is cached until the TTL of the response is getting ready to expire.

## API
The client only has one simple function. `getTurnInfo()`
The function takes the following arguments:

* `url`, This is a required argument, this should be the full turnRest servers API for your setup.
* `jwt`, This is optional (defaults to None)  If your setup requires a JWT to get turn credentials (a very good idea) then you should set this. 
* `force`, This forces a new request regardless of if there is one cached or not.  This should only really be used for testing or if you know what you are doing.

## Example

```javascript

let turnData = getTurnInfo("https://turn.rest.url.example.com", jwt=MyJWTVar, force=False)
console.log(turnData)

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
        "turn:turn.example.com?transport=tcp"
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
The username/password in the returned JSON are the username/password that are used to login to the turn server.

* Note: the username starts with a number that is the `epoch` time in seconds that the user's session expires (~15000 seconds when the request was made)

The `TTL` is how long this username/password will be valid for.

The `iceServers` section is a convenience, this is **currently** the format webRTC takes in the iceServers parameters so it is filled out for you.  Depending on how the server is configured this may or maynot include a stun server, and will have at least 1 turn server, though more are defenitly possible.
