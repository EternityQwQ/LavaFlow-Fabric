package dev.lavaflow.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueFamiliesTest {
    @Test void isComplete_bothPresent() { assertTrue(new QueueFamilies(0, 2).isComplete()); }
    @Test void isComplete_graphicsMissing() { assertFalse(new QueueFamilies(-1, 0).isComplete()); }
    @Test void isComplete_presentMissing() { assertFalse(new QueueFamilies(0, -1).isComplete()); }
    @Test void isComplete_bothMissing() { assertFalse(new QueueFamilies(-1, -1).isComplete()); }
    @Test void isUnified_sameFamily() { assertTrue(new QueueFamilies(1, 1).isUnified()); }
    @Test void isUnified_differentFamilies() { assertFalse(new QueueFamilies(0, 1).isUnified()); }
}
