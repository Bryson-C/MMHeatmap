package org.bc.MMheatmap.poller;

/**
 * This class is to be used to store interactions over a specific area. The area that this class covers is not defined within
 * the class itself, only the actions that happen in said area are stored here.
 *
 *  @author BC/Exo
 */
public class PlayerChunkInteractions {
    int places, breaks;

    /**
     * @see String#toString()
     */
    @Override
    public String toString() {
        return String.format("Blocks: %d placed, %d broken", places, breaks);
    }

    /**
     * Returns a Bukkit formated rich string for color support.
     * Should only be used in the context of sending info to a player. For printing the info to
     * the standard output/console, use PlayerChunkInteractions#toString();
     *
     * @return Returns a Bukkit formated rich string for color support
     */
    public String toRichMessage() {
        return String.format("Blocks: <green>%d placed<reset>, <red>%d broken", places, breaks);
    }

    /**
     * This function is to be used to calculate activity level of a specific area.
     * The actual area that the class "oversees" is to be handled externally, this class
     * is specifically for the actions, not the area.
     * <br><br>
     * An example of calculating activity would be:
     * if (places + breaks > 50) return places + breaks
     * else return 0;
     * <br><br>
     * In the above example, if the area has not had a combined 50 place and break actions,
     * then the chunk would not be marked as "active". An "active" chunk is determined by the
     * user of the code base.
     * -
     * TLDR: user defined formula for calculating activity level in an area.
     *
     * @return returns an activity score if above a threshold (determined by codebase user), otherwise 0
     */
    public int calculateActivityLevel() {
        return places + breaks;
    }
}
