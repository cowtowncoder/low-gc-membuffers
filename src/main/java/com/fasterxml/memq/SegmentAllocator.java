package com.fasterxml.memq;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Shared allocator object, used by all {@link MemQ} instances
 * that are part of a {@link MemQGroup}. It handles allocation
 * of new {@link Segment} instances, as well as sharing of
 * shared segments (above and beyond simple reuse that individual
 * queues can do).
 */
public class SegmentAllocator
{

}
