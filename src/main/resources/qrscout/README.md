This is the KoiBots clone of FRC 2713 Red Hawk Robotics QR Scout.

We blatently stole all of their ideas but re-implemented them with a
(hopefully) more straightforward design using only HTML, CSS, and javascript.
The only library added is QRious for QR-code generation.

The interface is built dynamically by loading a configuration file called
<code>config.json</code> which will be loaded relative to index.html.
It is in JSON format and follows the specification designed by FRC team
2713 and documented here:

https://github.com/FRC2713/QRScout?tab=readme-ov-file#configjson

The only unfortunate bit is that you must load the (small) web application
from a web server, because the <code>config.json</code> file must be
*loaded* by the script, and the script can't read local files.

There are a few differences between the original QR Scout and ours:

1. Simpler implementation (e.g. no build process required)
1. Image fields can have a "value" property which will be encoded in
   the QR code instead of the full URL of the image
1. Counter and range fields are currently handled exactly the same way
