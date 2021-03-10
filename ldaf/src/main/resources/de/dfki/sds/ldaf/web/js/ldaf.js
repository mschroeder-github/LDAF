
function formToJSON(elem) {
    var obj = {};
    elem.serializeArray().forEach(a => {
        obj[a.name] = a.value;
    });
    return obj;
}

function login(formData, successFunc, errorFunc) {
    //var formData = formToJSON($(loginFormSelector));
    //console.log(formData);

    $.ajax({
        type: "POST",
        url: "/auth/login",
        data: JSON.stringify(formData),
        dataType: "text",
        contentType: "application/json",
        success: function (data, textStatus, request) {
            //jwt is in cookie

            successFunc(request);

            //window.location = "/auth/me";

            //var jwt = request.getResponseHeader('Authorization');
            //localStorage.setItem("logd_auth", jwt);
        },
        error: function (request, textStatus, errorThrown) {
            //$('#loginFailedAlert').show();
            errorFunc(request);
        }
    });
}

function register(formData, successFunc, errorFunc) {
    //var formData = formToJSON($(registerFormSelector));
    //console.log(formData);

    $.ajax({
        type: "POST",
        url: "/auth/registration",
        data: JSON.stringify(formData),
        dataType: "text",
        contentType: "application/json",
        success: function (data, textStatus, request) {
            successFunc(request);
            /*
            $('#registerAlert').show();
            //copy&paste username to login
            $('#loginUserName').val($('#regUserName').val());
            $('#loginPassword').val("");
            //reset input
            $('#registerForm')[0].reset();
            */
        },
        error: function (request, textStatus, errorThrown) {
            errorFunc(request);
            /*
            $('#registerFailedAlert').text(request.responseText);
            $('#registerFailedAlert').show();
             * 
             */
        }
    });
}

function selfpatch(json, successFunc, errorFunc) {
    //console.log(window.location.pathname);
    //console.log(json);
    $.ajax({
        type: "PATCH",
        url: window.location.pathname,
        data: JSON.stringify(json),
        dataType: "text",
        contentType: "application/json",
        success: successFunc,
        error: errorFunc
    });
}

function extpatch(url, json, successFunc, errorFunc) {
    //console.log(window.location.pathname);
    //console.log(json);
    $.ajax({
        type: "PATCH",
        url: url,
        data: JSON.stringify(json),
        dataType: "text",
        contentType: "application/json",
        success: successFunc,
        error: errorFunc
    });
}

const delay = 500;

function initdebounce(id, func) {
    var elem = document.getElementById(id);
    elem.addEventListener("input", debounce(elem, func, delay));
    elem.addEventListener("directinput", func);
}

function inputwait(elem) {
    elem.style['border-color'] = 'red';
}

function inputsuccess(elem) {
    //elem.style['border-color'] = 'green';
    elem.style['border-color'] = null;
}

// https://davidwalsh.name/javascript-debounce-function
// Returns a function, that, as long as it continues to be invoked, will not
// be triggered. The function will be called after it stops being called for
// N milliseconds. If `immediate` is passed, trigger the function on the
// leading edge, instead of the trailing.
function debounce(elem, func, wait, immediate) {
	var timeout;
	return function() {
                inputwait(elem);
		var context = this, args = arguments;
		var later = function() {
			timeout = null;
			if (!immediate) func.apply(context, args);
		};
		var callNow = immediate && !timeout;
		clearTimeout(timeout);
		timeout = setTimeout(later, wait);
		if (callNow) func.apply(context, args);
	};
};


//==============================================================================

function labelPatcher(id) {
    propertyPatcher(id, "label");
}

function commentPatcher(id) {
    propertyPatcher(id, "comment");
}

function propertyPatcher(id, property, type) {
    initdebounce(id, function() { 
        var elem = this; 
        var json = {};
        if(type === 'object') {
            json[property] = [ elem.value ];
        } else {
            if(isNaN(elem.value)) {
                json[property] = elem.value;
            } else {
                json[property] = parseInt(elem.value);
            }
        }
        selfpatch(json, function() { inputsuccess(elem); }); 
    });
}

function propertyPatcherExt(url, id, property, type) {
    initdebounce(id, function() { 
        var elem = this; 
        var json = {};
        if(type === 'object') {
            json[property] = [ elem.value ];
        } else {
            if(isNaN(elem.value)) {
                json[property] = elem.value;
            } else {
                json[property] = parseInt(elem.value);
            }
        }
        extpatch(url, json, function() { inputsuccess(elem); }); 
    });
}

function imagePatcher(id, property) {
    initdebounce(id, function() { 
        var elem = this;
        var json = {};
        
        if(!isNaN(elem.value) && (elem.value === "" || parseInt(elem.value) <= 0)) {
            json[property] = [ ];
        } else {
            json[property] = [ "/upload/" + elem.value ];
        }
        selfpatch(json, function() { inputsuccess(elem); });
    });
}

function patchProperty(id, property) {
    var elem = document.getElementById(id);
    inputwait(elem);
    var json = {};
    json[property] = elem.value;
    selfpatch(json, function() { inputsuccess(elem); });
}

function patchSelectOnChange(id, property, currentValue) {
    var elem = document.getElementById(id);
    elem.value = currentValue;
    elem.addEventListener("change", function() {
        var elem = this; 
        var json = {}; 
        json[property] = [ elem.options[elem.selectedIndex].value ];
        inputwait(elem);
        selfpatch(json, function() { inputsuccess(elem); });
    });
}

function patchSelectOnChangeExt(url, id, property, currentValue) {
    var elem = document.getElementById(id);
    elem.value = currentValue;
    elem.addEventListener("change", function() {
        var elem = this; 
        var json = {}; 
        json[property] = [ elem.options[elem.selectedIndex].value ];
        inputwait(elem);
        extpatch(url, json, function() { inputsuccess(elem); });
    });
}

function timeSince(date) {
    if (typeof date !== 'object') {
      date = new Date(date);
    }

    var seconds = Math.floor((new Date() - date) / 1000);
    var intervalType;

    var interval = Math.floor(seconds / 31536000);
    if (interval >= 1) {
      intervalType = 'year';
    } else {
      interval = Math.floor(seconds / 2592000);
      if (interval >= 1) {
        intervalType = 'month';
      } else {
        interval = Math.floor(seconds / 86400);
        if (interval >= 1) {
          intervalType = 'day';
        } else {
          interval = Math.floor(seconds / 3600);
          if (interval >= 1) {
            intervalType = "hour";
          } else {
            interval = Math.floor(seconds / 60);
            if (interval >= 1) {
              intervalType = "minute";
            } else {
              interval = seconds;
              intervalType = "second";
            }
          }
        }
      }
    }

    if (interval > 1 || interval === 0) {
      intervalType += 's';
    }

    return interval + ' ' + intervalType;
};
