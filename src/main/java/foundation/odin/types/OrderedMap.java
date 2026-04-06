package foundation.odin.types;

import java.util.*;

public final class OrderedMap<K, V> implements Iterable<Map.Entry<K, V>> {
    private final List<Map.Entry<K, V>> entries;
    private final Map<K, Integer> index;

    public OrderedMap() {
        entries = new ArrayList<>();
        index = new LinkedHashMap<>();
    }

    public OrderedMap(int capacity) {
        entries = new ArrayList<>(capacity);
        index = new LinkedHashMap<>(capacity);
    }

    public OrderedMap(Iterable<Map.Entry<K, V>> source) {
        entries = new ArrayList<>();
        index = new LinkedHashMap<>();
        for (var entry : source) {
            set(entry.getKey(), entry.getValue());
        }
    }

    public int size() { return entries.size(); }

    public List<K> keys() {
        var keys = new ArrayList<K>(entries.size());
        for (var entry : entries) keys.add(entry.getKey());
        return Collections.unmodifiableList(keys);
    }

    public List<V> values() {
        var values = new ArrayList<V>(entries.size());
        for (var entry : entries) values.add(entry.getValue());
        return Collections.unmodifiableList(values);
    }

    public List<Map.Entry<K, V>> entries() {
        return Collections.unmodifiableList(entries);
    }

    public V get(K key) {
        Integer idx = index.get(key);
        if (idx == null) throw new NoSuchElementException("Key not found: " + key);
        return entries.get(idx).getValue();
    }

    public void set(K key, V value) {
        Integer idx = index.get(key);
        if (idx != null) {
            entries.set(idx, Map.entry(key, value));
        } else {
            index.put(key, entries.size());
            entries.add(Map.entry(key, value));
        }
    }

    public V tryGet(K key) {
        Integer idx = index.get(key);
        if (idx == null) return null;
        return entries.get(idx).getValue();
    }

    public boolean containsKey(K key) {
        return index.containsKey(key);
    }

    public boolean remove(K key) {
        Integer idx = index.get(key);
        if (idx == null) return false;
        entries.remove((int) idx);
        index.remove(key);
        for (int i = idx; i < entries.size(); i++) {
            index.put(entries.get(i).getKey(), i);
        }
        return true;
    }

    public void clear() {
        entries.clear();
        index.clear();
    }

    public Map.Entry<K, V> getAt(int i) {
        return entries.get(i);
    }

    public OrderedMap<K, V> copy() {
        return new OrderedMap<>(entries);
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return entries.iterator();
    }
}
