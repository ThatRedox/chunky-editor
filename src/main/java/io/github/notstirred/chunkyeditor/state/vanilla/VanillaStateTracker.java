package io.github.notstirred.chunkyeditor.state.vanilla;

import io.github.notstirred.chunkyeditor.VanillaRegionPos;
import io.github.notstirred.chunkyeditor.minecraft.WorldLock;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.log.Log;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Before any changes are made to the world, it should be checked against the current state to verify nothing has changed
 * If there are changes
 */
public class VanillaStateTracker {
    private static final int NO_STATE = -1;
    protected static final int HEADER_SIZE_BYTES = 4096;

    private final Path regionDirectory;

    private final List<State<VanillaRegionPos>[]> states = new ArrayList<>();
    private int currentStateIdx = NO_STATE;


    private final WorldLock worldLock;

    public VanillaStateTracker(Path worldDirectory, WorldLock worldLock) throws FileNotFoundException {
        this.regionDirectory = worldDirectory.resolve("region");
        this.worldLock = worldLock;
    }

    private State<VanillaRegionPos>[] internalSnapshot(List<VanillaRegionPos> regionPositions) throws IOException {
        InternalState[] stateArray = new InternalState[regionPositions.size()];
        for (int regionIdx = 0, regionPositionsSize = regionPositions.size(); regionIdx < regionPositionsSize; regionIdx++) {
            VanillaRegionPos regionPosition = regionPositions.get(regionIdx);
            Path regionPath = this.regionDirectory.resolve(regionPosition.fileName());

            byte[] data = new byte[HEADER_SIZE_BYTES];
            try (RandomAccessFile file = new RandomAccessFile(regionPath.toFile(), "r")) {
                file.readFully(data);
            }
            stateArray[regionIdx] = new InternalState(regionPosition, data);
        }
        return stateArray;
    }

    private State<VanillaRegionPos>[] externalSnapshot(List<VanillaRegionPos> regionPositions) throws IOException {
        ExternalState[] stateArray = new ExternalState[regionPositions.size()];
        for (int regionIdx = 0, regionPositionsSize = regionPositions.size(); regionIdx < regionPositionsSize; regionIdx++) {
            VanillaRegionPos regionPosition = regionPositions.get(regionIdx);
            Path regionPath = this.regionDirectory.resolve(regionPosition.fileName());

            byte[] data = Files.readAllBytes(regionPath);
            stateArray[regionIdx] = new ExternalState(regionPosition, data);
        }
        return stateArray;
    }

    private State<VanillaRegionPos>[] snapshot(List<VanillaRegionPos> regionPositions) throws IOException {
        return null;
    }

    /**
     * Retake the current snapshot
     */
    public void snapshotCurrentState(List<VanillaRegionPos> regionPositions) throws IOException {
        removeFutureStates();

        State<VanillaRegionPos>[] snapshot = snapshot(regionPositions);
        if (this.currentStateIdx == NO_STATE) {
            this.states.add(snapshot);
            this.currentStateIdx = 0;
        } else {
            this.states.set(this.currentStateIdx, snapshot);
        }
    }

    public void snapshotState(List<VanillaRegionPos> regionPositions) throws IOException {
        this.removeFutureStates();
        this.states.add(snapshot(regionPositions));
        this.currentStateIdx++;
    }

    /**
     * Remove all states after the current one
     */
    public void removeFutureStates() {
        if(this.currentStateIdx == NO_STATE) {
            return;
        }
        for (int i = 0; i < this.states.size() - this.currentStateIdx - 1; i++) {
            this.states.remove(currentStateIdx + 1);
        }
    }

    /**
     * Remove all header backups stored
     */
    public void removeAllStates() {
        this.states.clear();
    }
}
