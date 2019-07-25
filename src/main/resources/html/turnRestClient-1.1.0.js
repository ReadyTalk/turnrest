var TurnService = (function() {
  var __REST_PROMISE=JSON.stringify({p: null, done: false, good: false, expires: 0})
  var __REQS = {}

  function __fetchJSON(path, jwt=null) {
    return new Promise(function(resolve,reject){
      let myHeaders = new Headers()
      if(jwt != null) {
        myHeaders = new Headers({
          'Authorization': 'Bearer ' + jwt
        })
      }
      let myInit = { method: 'GET',
                   headers: myHeaders,
                   cache: 'no-store'
                  }

      let myRequest = new Request(path, myInit)

      fetch(myRequest).then(function(response) {
         resolve(response.json())
      })
      .catch(function(error) {
        reject(JSON.stringify(error))
      })
    })
  }

  function getInfo(turnURL, jwt=null, forceCall=false) {
    if(turnURL === null || turnURL === undefined || turnURL === "" || !turnURL.toLowerCase().startsWith("http")) {
      return new Promise(function(resolve, reject) {
        reject("Error processing TurnServiceInfo request, must provide a valid http/https url! provilded:\""+turnURL+"\"");
      })
    }
    if (__REQS[turnURL] === undefined) {
      __REQS[turnURL] = JSON.parse(__REST_PROMISE)
    }
    let prom = __REQS[turnURL];

    let isExpired = (new Date).getTime() > prom.expires
    if(prom.p == null || forceCall || (prom.done && ! prom.good) || isExpired) {
      prom.p = new Promise(function(resolve, reject) {
        let lj = __fetchJSON(turnURL, jwt)
        lj.then(function(obj){
          prom.expires = (new Date).getTime() + obj.ttl - 300
          prom.good = true
          prom.done = true
          resolve(obj);
        }, function(err){
          reject(err);
          prom.done = true
          prom.good = false
        })
      })
    }
    prom.done = false
    prom.good = false
    return prom.p
  }

  return {
    getTurnServiceInfo: function(turnURL, jwt=null, forceCall=false) {
      return getInfo(turnURL, jwt=jwt, forceCall=forceCall);
    }
  }
})()
