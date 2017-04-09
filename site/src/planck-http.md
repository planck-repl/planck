# planck.http

Planck HTTP functionality.

_Vars_

[get](#get)<br/>
[post](#post)<br/>

## Vars

### <a name="get"></a>get
`([url] [url opts])`
  Performs a GET request. It takes an URL and an optional map of options.
  These include:<br/>
  `:timeout`, number, default 5 seconds<br/>
  `:debug`, boolean, assoc the request on to the response<br/>
  `:accepts`, keyword or string. Valid keywords are `:json` or `:xml`<br/>
  `:content-type`, keyword or string Valid keywords are `:json` or `:xml`<br/>
  `:headers`, map, a map containing headers

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un  [::timeout ::debug ::accepts ::content-type ::headers])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status])`

### <a name="post"></a>post
`([url] [url opts])`
  
  Performs a POST request. It takes an URL and an optional map of options
  These options include the options for get in addition to:<br/>
  `:form-params`, a map, will become the body of the request, urlencoded<br/>
  `:multipart-params`, a list of tuples, used for file-upload<br/>
`{:multipart-params [["name" "value"]`<br/>
`["name" ["content" "filename"]]`<br/>

Spec<br/>
 _args_: `(cat :url string? :opts (? (keys :opt-un [::timeout ::debug ::accepts ::content-type ::headers ::body ::form-params ::multipart-params])))`<br/>
 _ret_: `(keys :req-un [::body ::headers ::status])`


