package org.knoxcraft.serverturtle;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.knoxcraft.turtle3d.KCTCommand;
import org.knoxcraft.turtle3d.KCTScript;
import org.knoxcraft.turtle3d.TurtleCommandException;

import net.canarymod.api.entity.living.EntityLiving;
import net.canarymod.api.world.World;
import net.canarymod.api.world.blocks.BlockType;
import net.canarymod.api.world.position.Direction;
import net.canarymod.api.world.position.Position;
import net.canarymod.chat.MessageReceiver;
import net.canarymod.logger.Logman;

public class TheoreticalTurtle
{
    public enum D {
        Forward, Backward, Left, Right, Up, Down 
    }

    //private KCTScript script;
    private Position relPos; //Get set when Sprite created (0,0,0)
    private Position worldPos; // Get set when Sprite created (game location when made)
    //All movement is in terms of relative pos, but are returned to game in terms of worldPos in order to be in correct locations
    private Direction dir;

    //OTHER VARIABLES
    private boolean bp = true;  //Block Place on/off
    private BlockType bt = BlockType.Stone;  //default turtle block type 
    private World world;  //World in which all actions occur
    private MessageReceiver sender;  //player to send messages to
    private Stack<BlockRecord> oldBlocks;  //original pos/type of all bricks laid by this turtle for undoing
    
    private KCTScript script;
    private Logman logger;
    private MagicBunny magicBunny;
    private List<KCTCommand> commandList;
    private int commandIndex=0;

    public TheoreticalTurtle(MessageReceiver sender, MagicBunny magicBunny, KCTScript script, Logman logger) {
        this.sender=sender;
        this.magicBunny=magicBunny;
        this.logger=logger;
        this.script=script;
        this.world=sender.asPlayer().getWorld();
        // convert to an ArrayList to make sure get() is O(1)
        this.commandList=new ArrayList<KCTCommand>(script.getCommands());

        //initialize undo buffer
        oldBlocks = new Stack<BlockRecord>();

        //Get origin Position and Direction
        worldPos = sender.asPlayer().getPosition();
        dir = sender.asPlayer().getCardinalDirection();

        //Make the Relative Position
        relPos = new Position(0,0,0);
    }

    public MagicBunny getMagicBunny() {
        return magicBunny;
    }
    
    public boolean hasMoreCommand() {
        return commandIndex < commandList.size();
    }

    public List<KCTCommand> executeNextCommands(int num) throws TurtleCommandException {
        int max=commandIndex+num;
        List<KCTCommand> result=new LinkedList<KCTCommand>();
        while (commandIndex<max && commandIndex<commandList.size()) {
            KCTCommand command=commandList.get(commandIndex);
            commandExecute(command);
            result.add(command);
            commandIndex++;
        }
        return result;
    }


    /*
     * Execute a command, Moving the sprite, updating facing dir, placing block, updating undo buffer, wait
     */
    private void commandExecute(KCTCommand c)throws TurtleCommandException{
        //get command info
        Map<String, Object> m = c.getArguments();
        String commandName = c.getCommandName();

        //execute command
        if (commandName.equals(KCTCommand.FORWARD)) {
            //Get Value
            int dist;
            if (!m.containsKey(KCTCommand.DIST)){ 
                dist = 1; //default
            }else{
                dist = toInt(m.get(KCTCommand.DIST));
            }

            //Get Current Pos -> At beginning of move
            //Move Sprite Forward
            spriteMove(dist, D.Forward, false);
            //->Place Blocks (Inside Sprite movement)
            //->Update Undo buffer


        }  else if (commandName.equals(KCTCommand.BACKWARD)) {
            //Get Value
            int dist;
            if (!m.containsKey(KCTCommand.DIST)){ 
                dist = -1; //default
            }else{
                dist = -toInt(m.get(KCTCommand.DIST));
            }
            //Get Current Pos -> At beginning of move
            //Move Sprite Backward
            spriteMove(dist, D.Backward, false);
            //->Place Blocks (Inside Sprite movement)
            //->Update Undo buffer

        } else if (commandName.equals(KCTCommand.RIGHT)) {
            //Get Value
            int dist;
            if (!m.containsKey(KCTCommand.DIST)){ 
                dist = 1; //default
            }else{
                dist = toInt(m.get(KCTCommand.DIST));
            }
            //Shift to the Right
            spriteMove(dist, D.Right, false);
            //->Place Blocks (Inside Sprite movement)
            //->Update Undo buffer

        } else if (commandName.equals(KCTCommand.LEFT)) {
            //Get Value
            int dist;
            if (!m.containsKey(KCTCommand.DIST)){ 
                dist = 1; //default
            }else{
                dist = toInt(m.get(KCTCommand.DIST));
            }
            //Shift to the Left
            spriteMove(dist, D.Left, false);
            //->Place Blocks (Inside Sprite movement)
            //->Update Undo buffer

        }else if (commandName.equals(KCTCommand.TURNRIGHT)) {
            //Get Value
            int ang;
            if (!m.containsKey(KCTCommand.DEGREES)){ 
                ang = 90; //default
            }else{
                ang = toInt(m.get(KCTCommand.DEGREES));
            }
            //Turn Right( Change Heading)
            spriteMove(ang, D.Right, true);
        } else if (commandName.equals(KCTCommand.TURNLEFT)) {
            //Get Value
            int ang;
            if (!m.containsKey(KCTCommand.DEGREES)){ 
                ang = 90; //default
            }else{
                ang = toInt(m.get(KCTCommand.DEGREES)); 
            }
            //Turn Left( Change Heading)
            spriteMove(ang, D.Left, true);
        } else if (commandName.equals(KCTCommand.PLACEBLOCKS)) {
            //Place blocks or just move
            if (m.containsKey(KCTCommand.BLOCKPLACEMODE)){ 
                boolean mode = (Boolean)m.get(KCTCommand.BLOCKPLACEMODE);
                spriteSetBlockPlace(mode);
            }

        } else if (commandName.equals(KCTCommand.SETPOSITION)) {
            //set Sprites relative position
            if (m.containsKey(KCTCommand.X) && m.containsKey(KCTCommand.Y) && m.containsKey(KCTCommand.Z))  {
                int x = toInt(m.get(KCTCommand.X)); 
                int y = toInt(m.get(KCTCommand.Y));
                int z = toInt(m.get(KCTCommand.Z));
                spriteSetRelPosition(x, y, z);
            }          

        } else if (commandName.equals(KCTCommand.SETDIRECTION)) {
            //set sprite direction
            if (m.containsKey(KCTCommand.DIR))  {
                int dir = toInt(m.get(KCTCommand.DIR));
                spriteSetDirection(dir);
            } 

        }  else if (commandName.equals(KCTCommand.UP)) {
            //Get Value
            int dist;
            if (!m.containsKey(KCTCommand.DIST)){ 
                dist = 1; //default
            }else{
                dist = toInt(m.get(KCTCommand.DIST));
            }
            //Shift Up
            spriteMove(dist, D.Up, false);
            //->Place Blocks (Inside Sprite movement)
            //->Update Undo buffer

        } else if (commandName.equals(KCTCommand.DOWN)) {
            //Get Value
            int dist;
            if (!m.containsKey(KCTCommand.DIST)){ 
                dist = -1; //default
            }else{
                dist = -toInt(m.get(KCTCommand.DIST));
            }
            //Shift Down
            spriteMove(dist, D.Down, false);
            //->Place Blocks (Inside Sprite movement)
            //->Update Undo buffer

        } else if (commandName.equals(KCTCommand.SETBLOCK)) {
            // Set block type
            int type;
            String strType = "";
            if (!m.containsKey(KCTCommand.BLOCKTYPE)){ //Not in arg map -> Default
                type = 1; //default is Stone
                spriteSetBlockType(type);
            }else{
                Object o = m.get(KCTCommand.BLOCKTYPE);
                if (o instanceof String) {//STR vs INT
                    strType = (String)o;
                    spriteSetBlockType(strType);
                } else{ // Otherwise its an int
                    type = toInt(o);
                    spriteSetBlockType(type);
                }
            }

        } else {            
            String msg=String.format("Unknown command: %s", commandName);
            logger.error(msg);
            throw new TurtleCommandException(msg);
        }

    }

    void sInit(MessageReceiver sender){
        //initialize undo buffer
        oldBlocks = new Stack<BlockRecord>();

        //record sender
        this.sender = sender;

        //GET WORLD
        world = sender.asPlayer().getWorld();

        //Get origin Position and Direction
        worldPos = sender.asPlayer().getPosition();
        dir = sender.asPlayer().getCardinalDirection();

        //Make the Relative Position
        relPos = new Position(0,0,0);
    }

    ////?????????????????????

    /**
     * Output a message to the player console.
     * 
     * @param sender
     * @param args
     */
    public void sConsole(String msg)
    {
        sender.message(msg); 
    }  

    /**
     * Turn block placement mode on/off.
     * 
     * @param sender
     * @param args
     */
    public void spriteSetBlockPlace(boolean mode)
    {
        bp = mode;
    }

    /**
     * Reports whether block placement mode is on.
     * @param sender
     * @param args
     */
    public void spriteBlockPlaceStatus()
    {
        if(bp)  {
            sConsole("Block placement mode on.");
        }  else {
            sConsole("Block placement mode off.");
        }
    }

    /**
     * Set turtle position (relative coords)
     * @param sender
     * @param args
     */
    public void spriteSetRelPosition(int x, int y, int z)
    {     
        relPos = new Position(x, y, z);
    }

    /**
     * Set turtle direction.  Number based.
     * 
     * @param sender
     * @param args
     */
    public void spriteSetDirection(int dir)
    {
        this.dir = (Direction.getFromIntValue(dir));
    }

    /**
     * Report current position (relative)
     */
    public void spriteReportPosition()
    {
        sConsole("" + relPos);
    }

    /**
     * Report current direction (relative)
     */
    public void spriteReportDirection()
    {
        sConsole("" + dir);
    }

    /**
     * Set block type (int based)
     * 
     * @param int
     */
    public void spriteSetBlockType(int blockType)
    {
        bt = BlockType.fromId(blockType);      
    }

    /**
     * set Block type (string/BlockType based)
     * 
     * @param sender
     * @param args
     */
    public void spriteSetBlockType(String blockType)
    {
        // TODO: BlockType.fromString() has a bug in it, but I can't get
        // CanaryMod and CanaryLib to both compile in order to fix it. So
        // I'm adding a workaround.
        try {
            if (blockType.contains(":")) {
                String[] idAndData=blockType.split(":");
                int id=Integer.parseInt(idAndData[0]);
                int data=Integer.parseInt(idAndData[1]);
                bt = BlockType.fromIdAndData(id, data);
            } else {
                bt = BlockType.fromId(Integer.parseInt(blockType));
            }
        } catch (NumberFormatException e) {
            logger.error(String.format("Cannot parse blockType %s; not changing the blockType", blockType));
        }
        logger.debug(String.format("block type: %s from %s", bt, blockType));
    }

    /**
     * Report current block type
     * 
     * @param sender
     * @param args
     */
    public void spriteReportBlockType()
    {
        if (!bp)  //don't allow if block placement mode isn't on
            sConsole("Block placement mode is not on.");

        //report current BT of turtle   
        sConsole("" + bt);
    }

    /**
     * Move / Turn
     * 
     * @param dist
     */
    public void spriteMove(int dist, D d, boolean turn)
    {
        //For length of dist
        for (int i = Math.abs(dist); i > 0; i--){
            //Get Sprite's Location
            Position tempPos = relPos;
            Direction tempDir = dir;
            //Move Sprite 
            sMove(dist, d, turn); //Moves sprite, updates relPos, dir
            //Place Block at Old location
            //-> Save old block for undo
            if (bp) {
                //keep track of original block to undo, if not already in stack
                BlockRecord br = new BlockRecord(world.getBlockAt(updateGamePos(tempPos)), world);
                if(!oldBlocks.contains(br))  {
                    oldBlocks.push(br);
                }
                //place new block in game
                world.setBlockAt(updateGamePos(tempPos), bt);
            }
            //Update Location

            magicBunny.teleportTo(relPos);
            //Place block if block placement mode on

        }
    }



    /**
     * Return whether block placement mode is on.
     * 
     * @return the value of bp
     */
    public boolean getBP()  {
        return bp;
    }

    /**
     * Return a copy of the stack of blocks replaced by this sprite (for undoing)
     * 
     * @return undo stack
     */
    public Stack<BlockRecord> getOldBlocks()  {
        //Woo!
        return oldBlocks;
    }    



    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //PRIVATE HELPER FUNCTIONS

    /**
     * Update game pos when need to interact with world
     */
    private Position updateGamePos(Position pos) {
        Position tempPos = new Position(0,0,0);
        //get origin coords
        int xo = worldPos.getBlockX();
        int yo = worldPos.getBlockY();
        int zo = worldPos.getBlockZ();     

        //get relative coords
        int xr = pos.getBlockX();
        int yr = pos.getBlockY();
        int zr = pos.getBlockZ();

        //update game position
        //for each coord, gamePos = originPos + relPos;
        tempPos.setX(xo+xr);
        tempPos.setY(yo+yr);
        tempPos.setZ(zo+zr);

        return tempPos;
    }

    /**
     * Helper class for movement
     */
    private void sMove(int dist, D d, boolean turn) {
        if (!turn){
            boolean up = false, down = false;
            if (d == D.Up){
                up = true;
            } else if (d == D.Down){
                down = true;
            }else{
                //Nothing
            }
            relPos = updatePos(relPos, updateDir(d, 0), up, down);
            magicBunny.teleportTo(relPos);//Doesn't change dir for sprite
        } else {//Only change direction
            dir = updateDir(d, dist);
            magicBunny.lookAt(relPos.getX(),relPos.getY(), relPos.getZ()); //Update Sprite
        }
    }
    /**
     * Updates direction
     */
    private Direction updateDir(D d, int ang) {
        int tempDir = dir.getIntValue();
        if(ang == 0){//For Shifting
            if (d == D.Left){
                tempDir = tempDir - 2;
            }else if(d == D.Right){ //Right
                tempDir = tempDir + 2;
            }else if(d == D.Backward){ 
                tempDir = tempDir + 4;
            }else{
                return dir; //Don't change, go forward
            }
        }else{
            int turns = ang/45;  //desired number of eighth turns
            turns = turns % 8;

            if (d == D.Left)  {  //turning left
                tempDir -= turns;
            }  else  {  //turning right
                tempDir += turns;
            }

            // have to make sure we don't end up with a negative direction
            tempDir = (tempDir+8) % 8;
        }
        //Return new value
        Direction rDir = Direction.getFromIntValue(tempDir);
        return rDir;

    }

    /**
     * Updates position
     */
    private Position updatePos (Position p, Direction d, boolean up, boolean down){ 

        int dn = d.getIntValue();  //get direction number

        //check if vertical motion
        if (up || down ){
            if (up)  {  //moving up
                //add y +1
                p.setY(p.getBlockY() + 1);

            }else  {  //otherwise moving down
                //subtract y -1
                p.setY(p.getBlockY() - 1);
            }

        }  else  {  //2D motion
            if(dn == 0){ //NORTH
                //subtract z -1
                p.setZ(p.getBlockZ() - 1);

            }else if(dn == 1){//NORTHEAST
                //subtract z -1
                //add x +1
                p.setZ(p.getBlockZ() - 1);
                p.setX(p.getBlockX() + 1);

            }else if(dn == 2){//EAST
                //add x +1
                p.setX(p.getBlockX() + 1);

            }else if(dn == 3){//SOUTHEAST
                //add z +1
                //add x +1
                p.setZ(p.getBlockZ() + 1);
                p.setX(p.getBlockX() + 1);

            }else if(dn == 4){//SOUTH
                //add z +1
                p.setZ(p.getBlockZ() + 1);

            }else if(dn == 5){//SOUTHWEST
                //add z +1
                //subtract x -1
                p.setZ(p.getBlockZ() + 1);
                p.setX(p.getBlockX() - 1);

            }else if(dn == 6){//WEST
                //subtract x -1
                p.setX(p.getBlockX() - 1);

            }else if(dn == 7){//NORTHWEST
                //subtract z -1
                //subtract x -1
                p.setZ(p.getBlockZ() - 1);
                p.setX(p.getBlockX() - 1);

            }else {
                //BAD STUFF
                //Not one of the 8 main directions.  
                //Will require more math, but maybe we don't want to worry about this case.
            }
        }
        return p;  //return updated position
    }

    /**
     * Helper method to cast object coming from command arg array to int.
     * @param o
     * @return o as an int
     */
    private static int toInt(Object o) {
        return (int)((Long)o).longValue();   //Magic hand wavey stuff
    }

    /**
     * Destroy the entityLiving (sprite)
     */
    public void destroy() {
        magicBunny.destroy();
    }

    public Logman getLogger() {
        return logger;
    }

    public KCTScript getScript() {
        return script;
    }

    public String getPlayerName() {
        return sender.asPlayer().getName();
    }
}