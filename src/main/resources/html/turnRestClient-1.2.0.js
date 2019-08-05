var TurnService = (function() {
  var __REST_PROMISE=JSON.stringify({p: null, done: false, good: false, expires: 0})
  var __REQS = {}

  function __fetchJSON(path) {
    var jwt = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : null;

    return new Promise(function (resolve, reject) {
      var myHeaders = new Headers();
      if (jwt != null) {
        myHeaders = new Headers({
          'Authorization': 'Bearer ' + jwt
        });
      }
      var myInit = { method: 'GET',
        headers: myHeaders,
        cache: 'no-store'
      };

      var myRequest = new Request(path, myInit);

      fetch(myRequest).then(function (response) {
        resolve(response.json());
      }).catch(function (error) {
        reject(JSON.stringify(error));
      });
    });
  }

  function getInfo(turnURL) {
    var jwt = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : null;
    var forceCall = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : false;

    if (turnURL === null || turnURL === undefined || turnURL === "" || !turnURL.toLowerCase().startsWith("http")) {
      return new Promise(function (resolve, reject) {
        reject("Error processing TurnServiceInfo request, must provide a valid http/https url! provilded:\"" + turnURL + "\"");
      });
    }
    if (__REQS[turnURL] === undefined) {
      __REQS[turnURL] = JSON.parse(__REST_PROMISE);
    }
    var prom = __REQS[turnURL];

    var isExpired = new Date().getTime() > prom.expires;
    if (prom.p == null || forceCall || prom.done && !prom.good || isExpired) {
      prom.p = new Promise(function (resolve, reject) {
        var lj = __fetchJSON(turnURL, jwt);
        lj.then(function (obj) {
          prom.expires = new Date().getTime() + obj.ttl - 300;
          prom.good = true;
          prom.done = true;
          resolve(obj);
        }, function (err) {
          reject(err);
          prom.done = true;
          prom.good = false;
        });
      });
    }
    prom.done = false;
    prom.good = false;
    return prom.p;
  }

  return {
    getTurnServiceInfo: function getTurnServiceInfo(turnURL) {
      var jwt = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : null;
      var forceCall = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : false;

      return getInfo(turnURL, jwt = jwt, forceCall = forceCall);
    }
  };
})();
