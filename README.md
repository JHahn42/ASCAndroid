# Armchair Stormchasers
Developed as a capstone project at Ball State University 2019

Variables that may need to be adjusted can be changed from the Constants java class, and include things like, the server IP, mapbox api key, refresh rate of information from server, and the mapbox style used if it needs to be changed.

The app uses socket emit and listeneres for all functionality involving the server, otherwise all methods for map and other screen functionality is arranged in sections in mainActivity.java.

The main game loop of updating marker position and UI elements are controlled from a timer created after the map and style is loaded. It includes an emit to get player update from the server, then updating the marker and other UI elements on the screen. Aside from weather, the rest of the app is handling UI and when the player is able to click on certain UI elements or not.

The assets used for the game are located in the res/drawable folder, and if needed can be edited if need be.

The string values for the UI are in the res/values folder and are applied to the UI.

This Armchair Stormchasers app was created by the Development Team, Robert Gunderson, Jacob Hahn, William Moore and Ian Pemberton
