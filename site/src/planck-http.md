# planck.http

Planck HTTP functionality.

_Vars_

[get](#get)<br/>
[head](#head)<br/>
[delete](#delete)<br/>
[post](#post)<br/>
[put](#put)<br/>
[patch](#patch)<br/>

## Vars

### <a name="get"></a>get
`([url] [url opts])`
  Performs a GET request. It takes an URL and an optional map of options.
  These include:<br/>
  `:timeout`, number, default 5 seconds<br/>
  `:debug`, boolean, assoc the request on to the response<br/>
  `:insecure`, proceed even if the connection is considered insecure<br/>
  `:accept`, keyword or string. Valid keywords are `:json` or `:xml`<br/>
  `:content-type`, keyword or string Valid keywords are `:json` or `:xml`<br/>
  `:headers`, map, a map containing headers<br/>
  `:user-agent`, string, the user agent header to send<br/>
  `:follow-redirects`, boolean, follow HTTP location redirects
  `:max-redirects`, number, maximum number of redirects to follow
  `:socket`, string, specifying a system path to a socket to use<br/>
  `:binary-response`, boolean, encode response body as vector of unsigned bytes

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un  [::timeout ::debug ::accept ::content-type ::headers ::socket ::binary-response ::insecure ::user-agent ::follow-redirects ::max-redirects])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status])`

### <a name="head"></a>head
`([url] [url opts])`
  Performs a HEAD request. It takes an URL and an optional map of options.
  These include:<br/>
  `:timeout`, number, default 5 seconds<br/>
  `:debug`, boolean, assoc the request on to the response<br/>
  `:insecure`, proceed even if the connection is considered insecure<br/>
  `:headers`, map, a map containing headers<br/>
  `:user-agent`, string, the user agent header to send<br/>
  `:socket`, string, specifying a system path to a socket to use

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un  [::timeout ::debug ::headers ::socket ::insecure ::user-agent])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status])`

### <a name="delete"></a>delete
`([url] [url opts])`
  Performs a DELETE request. It takes an URL and an optional map of options.
  These include:<br/>
  `:timeout`, number, default 5 seconds<br/>
  `:debug`, boolean, assoc the request on to the response<br/>
  `:headers`, map, a map containing headers<br/>
  `:user-agent`, string, the user agent header to send<br/>
  `:socket`, string, specifying a system path to a socket to use

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un  [::timeout ::debug ::headers ::socket ::insecure ::user-agent])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status])`
 
### <a name="post"></a>post
`([url] [url opts])`
  
  Performs a POST request. It takes an URL and an optional map of options
  These options include the relevant options for get in addition to:<br/>
  `:form-params`, a map, will become the body of the request, urlencoded<br/>
  `:multipart-params`, a list of tuples, used for file-upload<br/>
`{:multipart-params [["name" "value"]`<br/>
`["name" ["content" "filename"]]`<br/>

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un [::timeout ::debug ::accept ::content-type ::headers ::body ::form-params ::multipart-params ::socket ::user-agent])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status ::insecure])`

### <a name="put"></a>put
`([url] [url opts])`
  
  Performs a PUT request. It takes an URL and an optional map of options
  These options include the relevant options for get in addition to:<br/>
  `:form-params`, a map, will become the body of the request, urlencoded<br/>
  `:multipart-params`, a list of tuples, used for file-upload<br/>
`{:multipart-params [["name" "value"]`<br/>
`["name" ["content" "filename"]]`<br/>

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un [::timeout ::debug ::accept ::content-type ::headers ::body ::form-params ::multipart-params ::socket ::user-agent])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status ::insecure])`

### <a name="patch"></a>patch
`([url] [url opts])`
  
  Performs a PATCH request. It takes an URL and an optional map of options
  These options include the relevant options for get in addition to:<br/>
  `:form-params`, a map, will become the body of the request, urlencoded<br/>
  `:multipart-params`, a list of tuples, used for file-upload<br/>
`{:multipart-params [["name" "value"]`<br/>
`["name" ["content" "filename"]]`<br/>

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un [::timeout ::debug ::accept ::content-type ::headers ::body ::form-params ::multipart-params ::socket ::insecure ::user-agent])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status])`
