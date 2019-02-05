function __loadJSON(path, async=true) {
  let xobj = new XMLHttpRequest();
  xobj.overrideMimeType("application/json");
  xobj.open('GET', path, async);
  if(async) {
    xobj.send(null);
    let promise = new Promise(function(resolve, reject) {
      xobj.onreadystatechange = function () {
        if (xobj.readyState == 4) {
          if(xobj.status == "200") {
            resolve(JSON.parse((xobj.responseText)));
          } else {
            reject(Error("Bad Response:"+xobj.status));
          }
        }
      };
    });
    return promise;
  } else {
    xobj.send(null);
    if (xobj.readyState == 4 && xobj.status == "200") {
      return xobj.responseText;
    } else {
      throw Error("Bad Response:"+xobj.status);
    }
  }
}

var __REST_PROMISE = null;

function getTurnRestInfo(turnURL, jwt=null, forceCall=false) {
  
  if(__REST_PROMISE == null || forceCall || (__REST_PROMISE.done && ! __REST_PROMISE.good)) {
    __REST_PROMISE = new Promise(function(resolve, reject) {
      let lj = __loadJSON(turnURL)
      lj.then(function(obj){
        resolve(obj);
        __REST_PROMISE.good = true;
        __REST_PROMISE.done = true;
      }, function(err){
        reject(err);
        __REST_PROMISE.done = true;
        __REST_PROMISE.good = false;
      });
    });
  } 
  __REST_PROMISE.done = false;
  __REST_PROMISE.good = false;
  return __REST_PROMISE;
}

