# Minecraft Multiagent Data Tracking Plugin

Spigot plugin developed for ASIST search-and-rescue UROP, Summer 2020. 

Note that along with the documentation below, the project itself has a detailed javadoc that can be accessed by cloning the repository and opening the file **javadocs/index.html**.

# Table of Contents

## **Using the Plugin**

[Main Features](#main-features)

[Setting up server to test recommendation system locally](#setting-up-server-to-test-recommendation-system-locally)

[How to start missions](#how-to-start-missions)

[Testing the raycasting visibility algorithm](#testing-the-raycasting-visibility-algorithm)

## **Developing the Plugin**

[How does the mission file structure work?](#how-does-the-mission-file-structure-work)

[How to create new missions](#how-to-create-new-missions)

[How does the best path algorithm work?](#how-does-the-best-path-algorithm-work)

# Main Features:

- Raycasting algorithm that can maintain a real-time list of blocks that are currently visible to a player
- Help players traverse through missions by giving real-time recommendations in the form of "best path algorithms" through the entire map, thus finding victims as fast as possible
- Reduce cognitive load by dynamically keeping track of blockages and new holes between rooms
- Provide a simple map interface to both let the player know where they are in the mission, as well as annotate important features of the current mission (such as where victims were left behind, where blockages/holes are, and where the best path through the map is)
- Maintain estimated player trajectories and inform the player of real-time calculated times it will take to traverse through all rooms in the map

![Annotated Map Example](https://github.com/tazadejava/minecraft-multiagent-data-tracking/blob/master/readme-resources/annotated-map-example.png?raw=true)
*Example of an annotated map for the Sparky map. Note that the player is currently located on the red cursor, rooms where victims still reside contain a rectangular red overlay, and blockages/holes between rooms are marked with X's and O's, respectively. The best path recommendation system is shown in green circles and purple lines.*

# Setting up server to test recommendation system locally

**The server files can be downloaded here:** [Link](https://github.com/tazadejava/minecraft-multiagent-data-tracking/raw/master/DataTrackingTestServer.zip)

The server is configured to work on Minecraft version 1.16.1, but it should be able to work on newer/older versions of Minecraft given that the plugin's Maven libraries are updated, the plugin is repackaged, and the Paper version that runs the server is updated to the desired version.

To run the server locally and test out the recommendation system, simply unzip the downloaded server file, then run the paper.jar inside the server folder. For simplicity, runtime scripts have been provided for you to easily run the server.

For Windows:

- Run the server by executing the RUN.bat file

For Linux/Mac:

- Run the server by executing the start&#46;sh file

## Overview:

1) Unzip the DataTrackingTestServer.zip file. This holds the Minecraft server that you need to run and join.

2) Run the paper.jar file via the instructions above. Wait until the server has started up. This should happen when the terminal stops creating new output and it states "Done (XXXs)".

3) Open Minecraft for version 1.16.1 (unless configured differently), and join via the Server Address **localhost**

4) See below on guides on either starting the mission and recommendation system to test out for yourself, or how to test out the raycasting algorithm! 

5) If you are not in creative mode or want to change your gamemode to fly around the map, first in the terminal type "op NAME", where NAME is your username, then you can type in Minecraft "gamemode creative/survival" to change your gamemode. You may need op permissions to execute some commands.

# How to start missions

To start the mission, you will be using the **/mission** command created for this plugin. In the server zip, there will be two already pre-loaded missions: Sparky and Falcon. You can start the mission via typing the command:

        /mission start Sparky

or

        /mission start Falcon

This will automatically start the mission, give all players maps, and teleport all players to the beginning of the map. Then, as players walk around the map, the recommendation system will update in real-time and guide them through the map.

Some other helpful commands that can be used:

List all available missions:

        /mission list

Abort a mission and don't save a log for it:

        /mission abort

# Testing the raycasting visibility algorithm

**Note that you do not need to clone this repo to test the raycasting algorithm. Simply download the server file above to test the algorithm, and you can use the repo to view the source and see how the algorithm is implemented.**

1) Follow the steps above to setup the server and log onto the server using a Minecraft client.

2) To start raycasting, you can type any of the following commands in Minecraft:

- "/mission raycast"
    - This will start a continuous raycast that will update every 4 ticks (there are 20 ticks in a second)
    - This will "paint" glass onto all the blocks that you have seen since the command started
        - Simply walk around to see which blocks are being viewed
    - When you want to stop, SNEAK (hold shift) for about 2 seconds until it says "Abort raycaster"
    - When you want to reset the blocks to normal, type "/mission raycastreset"
- "/mission raycastdiscrete"
    - This is like the first one, but it will only raycast once before resetting the blocks to normal, then it raycasts again.
    - WARNING: due to the way Minecraft renders client-side blocks, this flickers extremely quickly. I would not recommend using this command if this would bother you, as it can be visually extremely difficult. Use either the raycast command above or see the raycastonce command below for alternatives.
- "/mission raycastonce"
    - This will only run the algorithm once, so you can see what is currently visible at your current view.
    - Again, type "/mission raycastreset" to reset the blocks to normal.
    
## Important classes that implement this algorithm

### MissionCommandHandler

This class is in charge of handling all commands that start with the word "mission". You can see that it calls a switch method to determine which command was used, then it runs the algorithm depending on which raycasting algorithm was used (see raycastTest method and restoreBlocks method to see how we can interact with the results of the algorithm) 

### PreciseVisibleBlocksRaycaster

This class implements the raycasting algorithm. Simply run getVisibleBlocks method and you will get a list of blocks that are visible to the player. Optional ignoredBlocks method is not currently in use, so just pass null or use the other method. There are a few helper methods that help determine the raycasting algorithm, but essentially it will run like described below:

1) See what block the player is currently looking at. There is a max distance constant to save performance and a timeout function to also save performance.

2) Look at the air block that is in front of this block the player is looking at.

3) Now, expand out in all 6 directions (north west up down east south) and look for air blocks that have a solid block (anything but air) next to it. Add to an open list, then continue to spread out (this algorithm is similar to A* algorithm).

4) If air has no solid block next to it, then we forbid it from expanding out to another air block that has no solid block next to it. This makes the algorithm prefer air blocks that are next to solid blocks, so we can ensure best performance and only blocks that are visible to the player.

5) The FOVBounds class will calculate vectors to determine if a block is in the FOV of a player. It is an estimate.

6) Then, we will raycast from the player eye location to the block itself, based on which direction we see it in (it will raycast to the face of the block). If we hit it, it is visible. Otherwise, we check the hit block for blocks around it as a "fuzzy detection" to see if we missed it slightly, then add anyways.

7) The hyperPrecision mode was implemented recently, and will basically shoot out up to 5 rays from the player eye location to different parts of the block to accurately check if we can see the block or not. It is also more computationally intense, but it leads to extremely accurate results. In testing, however, I did not notice a difference in performance between the regular raycasting and hyperPrecision raycasting.

# How does the mission file structure work?

The missions are stored in a particular way, all within the **plugins/ActionTrackerPlugin** folder.

The file that registers all missions is **missions.json**. This will store a list of missions that are loaded and associate them with the respective sub-folder that contains more information about the mission.

The long-string-named folders with random letters and numbers (UUID) are the mission-specific data folders. This will hold information that will help the mission determine where the rooms in the map are, as well as create an internal graph representation of the map that is used to calculate best path recommendations for the player.

Typically, you will not need to edit any of these files directly, as there exists commands within the **/mission** command that will do everything for you. However, everything is saved in the JSON file format, so they are relatively easy to edit if needed.

# How to create new missions

All missions will need to create an internal graphical representation of the map that encompasses it. This means that the rooms and important hallway points need to be mapped (a hallway point is defined whenever the player has to make a decision, such as turning left or going forward).

To create a new mission, you have two choices:

1) Manually create the mission and its room and decision points. This is harder, and was superseded by the creation of the Graph Generator algorithm to help simplify this process. To get started with this method, begin by looking at the possible **/mission** commands via the **MissionCommandHandler** class, particularly starting with **/mission create**.

2) Recommended: use the Graph Generator method and a .csv file of the map to automatically generate a room and decision mapping of the map. This will automate much of the work needed to create missions. To automatically generate a mission, you will need to obtain the top-left coordinate of the block that is on the top-left of the .csv file in the actual map. Get the X, Y, and Z coordinates of this location (this can be seen via pressing F3). Then, stand and look exactly where you want all players to spawn when the mission begins. Finally, call this command to automatically generate the mission:

        /mission import <NAME OF MISSION> <CSV FILENAME (don't put .csv)> <X> <Y> <Z>

    - You should have the .csv file inside the plugins/ActionTrackerPlugin folder of your server.

    - The mission duration defaults to 1000 seconds. To change this value, type:

            /mission set <NAME OF MISSION> duration <DURATION IN SECONDS>

After you create the mission and all required components are completed, you can start the mission like normal via the **/mission start** command.

# How does the best path algorithm work?

The best path algorithm uses an internal graph representation of the map, as well as a running tracker of where the player is currently located on the map, to give the player recommendations of where they can go next to save as many victims as possible in the shortest amount of time.

Essentially, the best path algorithm uses the graph representation of the map to find shortest paths, and it does this via a limited horizon search and a greedy algorithm afterwards.

## Graphical representation

![Graph Example](https://github.com/tazadejava/minecraft-multiagent-data-tracking/blob/master/readme-resources/internal-graph-representation.png?raw=true)
*Shown is a series of room vertices, marked in red text, and decision vertices, marked in green. The room vertices represent the rooms in the map, AKA rooms where victims may reside, and the decision vertices represent any location in the hallway where the player will have to make a choice. For example, for decision point 15, the player has to decide which of 4 different directions to move in. All rooms will have a decision point outside of the door leading into the room, since the player will have to decide whether or not to enter the room or keep moving through the hallway.*

As described above, the graph uses two types of vertices: room vertices and decision vertices, to represent the missions. Paths between these vertices are pre-calculated, and the distance between any two vertices in blocks is used as the edge weight. In turn, this graphical representation can be used to tell the player how to get from one room to another by traversing through decision points.

## Algorithm Overview

The algorithm uses two different best path algorithms to tell the player where to go. A high-level overview can be found below:

**Part 1: Limited Horizon Search**

First, the algorithm will start at the player's current vertex as the starting node. It will begin by doing a limited horizon search to find a longer-term shortest path. To do this:

1) First, calculate the all-pairs-shortest-paths between all rooms and simplify the graph by only considering the distance between rooms without considering decision points. This is done by finding the shortest path between all rooms and storing this path in a mapping to be restored later.

2) Now, we can look at the two shortest paths from the current vertex to any room that has not yet been visited.

3) For each path, repeat step 2 from now the room vertex that was chosen, finding the top 2 rooms that are closest that have not been visited.

4) Repeat this process a total of 5 times, until 2^5 = 32 paths have been found. Now, we will choose the path that is OVERALL the shortest path (AKA shortest distance overall). This constitutes the first part of our best path!

**Part 2: Greedy Search**

Now that we have the first 5 vertices for the player to go to, we want to get an estimate of where the player can go next to reach all remaining rooms. To do this in real-time, we will perform a simple greedy algorithm that finds the next room that is closest to the current vertex, makes that the current vertex, and repeat, until all rooms have been traversed. Now, we have a recommended path through all rooms that we can use to create recommendations! Additionally, we have an estimate of path length that we can use to tell the player whether or not they can finish the map before the timer runs out. 