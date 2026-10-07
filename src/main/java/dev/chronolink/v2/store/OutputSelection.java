package dev.chronolink.v2.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagList;

/** A small, ordered allow-list; media can contain liquids AND gases at the same time. */
public final class OutputSelection {
    public static final int MAX_KEYS = 9;
    private final boolean items;
    private final List<Key> keys = new ArrayList<Key>();
    public OutputSelection(boolean items) { this.items = items; }
    public boolean accepts(Key key) { return key != null && (items ? key.kind == Key.ITEM : key.isFluid()); }
    public boolean isEmpty() { return keys.isEmpty(); }
    public int size() { return keys.size(); }
    public boolean contains(Key key) { return keys.contains(key); }
    public List<Key> keys() { return Collections.unmodifiableList(new ArrayList<Key>(keys)); }
    public void clear() { keys.clear(); }
    public boolean replace(Key key) {
        if (!accepts(key)) return false;
        keys.clear(); keys.add(key); return true;
    }
    /** Returns false on wrong kind or full selection, without changing existing filters. */
    public boolean toggle(Key key) {
        if (!accepts(key)) return false;
        if (keys.remove(key)) return true;
        if (keys.size() >= MAX_KEYS) return false;
        keys.add(key); return true;
    }
    public NBTTagList write() {
        NBTTagList out = new NBTTagList();
        for (Key key : keys) out.appendTag(key.write());
        return out;
    }
    /** Transactional load: invalid entries must not silently erase an existing selection. */
    public void read(NBTTagList list) {
        if (list.tagCount() > MAX_KEYS) throw new IllegalArgumentException("Too many selected resources");
        List<Key> next = new ArrayList<Key>();
        for (int i = 0; i < list.tagCount(); i++) {
            Key key = Key.read(list.getCompoundTagAt(i));
            if (!accepts(key) || next.contains(key)) throw new IllegalArgumentException("Unknown or repeated selection");
            next.add(key);
        }
        keys.clear(); keys.addAll(next);
    }
    public String label() {
        return keys.isEmpty() ? "未指定" : keys.get(0).label() + (keys.size() > 1 ? " +" + (keys.size() - 1) : "");
    }
}
