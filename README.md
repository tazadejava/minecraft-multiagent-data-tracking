# Minecraft Multiagent Data Tracking Plugin

Spigot plugin developed for Genesis Recommendation System UROP, Summer 2020.

## Main Features:

- Raycasting algorithm that can maintain a real-time list of blocks that are currently visible to a player
- Help players traverse through missions by giving real-time recommendations in the form of best path algorithms through the entire map, thus finding victims as fast as possible
- Reduce cognitive load by dynamically keeping track of blockages and new holes between rooms
- Provide a simple map interface to both let the player know where they are in the mission, as well as annotate important features of the current mission (such as where victims were left behind, where blockages/holes are, and where the best path through the map is)
- Maintain estimated player trajectories and inform the player of real-time calculated times it will take to traverse through all rooms in the map

![Annotated Map Example](https://github.com/tazadejava/minecraft-multiagent-data-tracking/readme-resources/annotated-map-example.png?raw=true)
Example of an annotated map for the Sparky map. Note that the player is currently located on the red cursor, rooms where victims still reside contain a rectangular red overlay, and blockages/holes between rooms are marked with X's and O's, respectively. The best path recommendation system is shown in green circles and purple lines.

# Quick Start: how to test the raycasting visibility algorithm

**Note that you do not need to clone this repo to test the raycasting algorithm. Simply download the server file to test the algorithm, and you can use the repo to view the source and see how the algorithm is implemented.**

1) UNZIP the DataTrackingTestServer zip file. This will hold the Minecraft server that you will need to run and join.

2) Within the DataTrackingTestServer folder, there should be a start.sh file. Execute this file and the server should start up (this works for Mac and Linux. For windows computers, use the RUN.bat file instead). The server is ready when it states "Done (XXXs)". The server is on 1.16.1.

3) Open up Minecraft and if you are on the same computer as the server, join via the address: localhost

4) If you are not in creative mode or want to change your gamemode to fly around the map, first in the terminal type "op NAME", where NAME is your username, then you can type in Minecraft "gamemode creative/survival" to change your gamemode.

5) To start raycasting, you can type any of the following commands in Minecraft:

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
    
# Important classes that implement this algorithm

## MissionCommandHandler

This class is in charge of handling all commands that start with the word "mission". You can see that it calls a switch method to determine which command was used, then it runs the algorithm depending on which raycasting algorithm was used (see raycastTest method and restoreBlocks method to see how we can interact with the results of the algorithm) 

## PreciseVisibleBlocksRaycaster

This class implements the raycasting algorithm. Simply run getVisibleBlocks method and you will get a list of blocks that are visible to the player. Optional ignoredBlocks method is not currently in use, and is currently in testing, so for now just pass null or use the other method. There are a few helper methods that help determine the raycasting algorithm, but essentially it will run like how I described during the meeting:

1) See what block the player is currently looking at. There is a max distance constant to save performance and a timeout function to also save performance

2) Look at the air block that is in front of this block the player is looking at

3) Now, expand out in all 6 directions (north west up down east south) and look for air blocks that have a solid block (anything but air) next to it. Add to an open list, then continue to spread out (this algorithm is similar to A* algorithm)

4) If air has no solid block next to it, then we forbid it from expanding out to another air block that has no solid block next to it. This makes the algorithm prefer air blocks that are next to solid blocks, so we can ensure best performance and only blocks that are visible to the player.

5) The FOVBounds class will calculate vectors to determine if a block is in the FOV of a player. It is an estimate.

6) Then, we will raycast from the player eye location to the block itself, based on which direction we see it in (it will raycast to the face of the block). If we hit it, it is visible. Otherwise, we check the hit block for blocks around it "fuzzy detection" to see if we missed it slightly, then add anyways.

7) The hyperPrecision mode was implemented recently, and will basically shoot out up to 5 rays from the player eye location to different parts of the block to accurately check if we can see the block or not. It is also more computationally intense, but it leads to extremely accurate results.

# Remarks

Let me know if anything is confusing or you have questions about something else related to the plugin (or plugins in general)! A lot of the code is still in progress, and I will be adding comments/cleaning up code as time goes on.