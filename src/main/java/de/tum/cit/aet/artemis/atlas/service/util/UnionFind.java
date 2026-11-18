package de.tum.cit.aet.artemis.atlas.service.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class UnionFind<T> {

    private final Map<T, T> parentMap = new LinkedHashMap();

    private final Map<T, Integer> rankMap = new HashMap();

    private int count;

    public UnionFind(Collection<T> elements) {
        for (var element : elements) {
            parentMap.put(element, element);
            rankMap.put(element, 0);
        }
        count = elements.size();
    }

    /**
     * Adds an element to the UnionFind data structure.
     *
     * @param element the element
     */
    public void addElement(T element) {
        if (parentMap.containsKey(element)) {
            return;
        }
        parentMap.put(element, element);
        rankMap.put(element, 0);
        count++;
    }

    /**
     * Finds the representative element of the set that the given element is in.
     *
     * @param element the element
     * @return the representative element of the set
     */
    public T find(T element) {
        if (!this.parentMap.containsKey(element)) {
            throw new IllegalArgumentException("element is not contained in this UnionFind data structure: " + element);
        }
        else {
            T current = element;

            while (true) {
                T root = this.parentMap.get(current);
                if (root.equals(current)) {
                    root = current;

                    T parent;
                    for (current = element; !current.equals(root); current = parent) {
                        parent = this.parentMap.get(current);
                        this.parentMap.put(current, root);
                    }

                    return root;
                }

                current = root;
            }
        }
    }

    /**
     * Unions two sets that are represented by the given elements.
     *
     * @param element1 the first element
     * @param element2 the second element
     */
    public void union(T element1, T element2) {
        if (!this.parentMap.containsKey(element1)) {
            throw new IllegalArgumentException("element1 is not contained in this UnionFind data structure: " + element1);
        }
        else if (!this.parentMap.containsKey(element2)) {
            throw new IllegalArgumentException("element2 is not contained in this UnionFind data structure: " + element2);
        }
        T parent1 = this.find(element1);
        T parent2 = this.find(element2);
        if (!parent1.equals(parent2)) {
            int rank1 = this.rankMap.get(parent1);
            int rank2 = this.rankMap.get(parent2);
            if (rank1 > rank2) {
                this.parentMap.put(parent2, parent1);
            }
            else if (rank1 < rank2) {
                this.parentMap.put(parent1, parent2);
            }
            else {
                this.parentMap.put(parent2, parent1);
                this.rankMap.put(parent1, rank1 + 1);
            }

            this.count--;
        }
    }

    /**
     * Checks if two elements are in the same set.
     *
     * @param element1 the first element
     * @param element2 the second element
     * @return true if the elements are in the same set, false otherwise
     */
    public boolean inSameSet(T element1, T element2) {
        return this.find(element1).equals(this.find(element2));
    }

    /**
     * Returns the number of sets.
     *
     * @return the number of sets
     */
    public int numberOfSets() {
        return this.count;
    }

    /**
     * Returns the number of elements in the union find data structure.
     *
     * @return the number of elements
     */
    public int size() {
        return this.parentMap.size();
    }
}
