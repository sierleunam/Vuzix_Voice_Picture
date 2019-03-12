
# Vuzix Voice Picture
[![Build Status](https://travis-ci.com/sierleunam/Vuzix_Voice_Picture.svg?branch=master)](https://travis-ci.com/sierleunam/Vuzix_Voice_Picture)

Simple application for Vuzix Glasses that can be controlled by android intents.
This application enables voice controlled applications in the background to send commands to take pictures.

---
## Custom Intent:
"com.sysdevmobile.vuzixvoicepicture.takepicture"
with Boolean Extra 'action' set to TRUE or FALSE to take a picture or exit the app.

--- 
After the picture is taken two files are created in the default Downloads folder:

- image.filename - text file with the picture filename
- Jpg file with the name listed in the previous file.
