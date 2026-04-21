package com.fastasyncworldedit.core.history.change;

import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.queue.implementation.SingleThreadQueueExtent;
import com.fastasyncworldedit.core.util.ExtentTraverser;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.world.storage.InvalidFormatException;
import com.sk89q.worldedit.world.block.BlockState;

public class MutableBlockChange implements Change {

    public int z;
    public int y;
    public int x;
    public int ordinal;


    public MutableBlockChange(int x, int y, int z, int ordinal) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.ordinal = ordinal;
    }

    @Override
    public void undo(UndoContext context) throws WorldEditException {
        create(context);
    }

    @Override
    public void redo(UndoContext context) throws WorldEditException {
        create(context);
    }

    public void create(UndoContext context) {
        Extent writeExtent = resolveWriteExtent(context);
        try {
            writeExtent.setBlock(x, y, z, BlockState.getFromOrdinal(ordinal));
        } catch (RuntimeException e) {
            if (isKnownExternalLoggingFailure(e)) {
                WorldEdit.logger.warn(
                        "Ignoring external plugin exception while applying block history at {},{},{}: {}",
                        x, y, z, e.getMessage()
                );
                return;
            }
            throw e;
        }
    }

    private static Extent resolveWriteExtent(UndoContext context) {
        Extent extent = context.getExtent();
        if (!Fawe.isMainThread() && extent instanceof EditSession editSession) {
            SingleThreadQueueExtent queueExtent = new ExtentTraverser<>(editSession.getBypassAll())
                    .findAndGet(SingleThreadQueueExtent.class);
            return queueExtent != null ? queueExtent : editSession.getBypassAll();
        }
        return extent;
    }

    private static boolean isKnownExternalLoggingFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Thread failed main thread check")) {
                return true;
            }
            if (current instanceof InvalidFormatException invalidFormatException) {
                String invalidMessage = invalidFormatException.getMessage();
                if (invalidMessage != null && invalidMessage.contains("SpawnData")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

}
