# Armchair Stormchasers
Developed as a capstone project at Ball State University 2019

Variables that may need to be adjusted can be changed from the Constants java class, and include things like, the server IP, mapbox api key, refresh rate of information from server, and the mapbox style used if it needs to be changed.

The app uses socket emit and listeneres for all functionality involving the server, otherwise all methods for map and other screen functionality is arranged in sections in mainActivity.java.

The main game loop of updating marker position and UI elements are controlled from a timer created after the map and style is loaded. It includes an emit to get player update from the server, then updating the marker and other UI elements on the screen. Aside from weather, the rest of the app is handling UI and when the player is able to click on certain UI elements or not.

The assets used for the game are located in the res/drawable folder, and if needed can be edited if need be.

The string values for the UI are in the res/values folder and are applied to the UI.

This Armchair Stormchasers app was created by the Development Team, Robert Gunderson, Jacob Hahn, William Moore and Ian Pemberton

## Known Issues
* Player info is currently stored app side, meaning to ensure a user has the most up to date info and does not lose progress, they need to log into the server at end of day, or at least after they've exited their last storm polygon for the day, and have the info saved locally.
* Overlapping weather polygons inside of the same layer do not draw in the overlapped portion, but players will still gain points in these areas
* If a player logs in for the first time, but does not choose a starting location and then logs out, when they log in the next time they get the Beginning of Day Screen and can choose the continue option to get the score multiplyer, but they will start out at the default location of BSU's Frog Baby.
* There is currently nothing preventing players from traveling outside of the US, other than a lack of storm data.
* On the login screen the logout button is still pressable while invisible, causing the server to send an error message that the user must log in to log out when pressed.
* Going from End of Day to Beginning of Day while logged into the server acts a bit wonky, as it has been largely untested, so it may be best to exit and restart the app when starting on a new day.
