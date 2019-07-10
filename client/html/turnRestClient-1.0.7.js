var __REST_PROMISE={p: null, done: false, good: false, expires: 0}

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



function getTurnInfo(turnURL, jwt=null, forceCall=false) {
  let isExpired = (new Date).getTime() > __REST_PROMISE.expires
  
  if(__REST_PROMISE.p == null || forceCall || (__REST_PROMISE.done && ! __REST_PROMISE.good) || isExpired) {
    __REST_PROMISE.p = new Promise(function(resolve, reject) {
      let lj = __fetchJSON(turnURL, jwt)
      lj.then(function(obj){
        __REST_PROMISE.expires = (new Date).getTime() + obj.ttl - 300
        __REST_PROMISE.good = true
        __REST_PROMISE.done = true
        resolve(obj);
      }, function(err){
        reject(err);
        __REST_PROMISE.done = true
        __REST_PROMISE.good = false
      })
    })
  }
  __REST_PROMISE.done = false
  __REST_PROMISE.good = false
  return __REST_PROMISE.p
}

