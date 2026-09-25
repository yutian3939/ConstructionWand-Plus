package com.xinyihl.constructionwandlegacy.basics.pool;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class RandomPool<T> implements IPool<T> {
    private final Random random;
    private final HashMap<T, Integer> elements;
    private HashSet<T> pool;

    public RandomPool(Random random) {
        this.random = random;
        this.elements = new HashMap<>();
        reset();
    }

    @Override
    public void add(T element) {
        addWithWeight(element, 1);
    }

    @Override
    public void remove(T element) {
        elements.remove(element);
        pool.remove(element);
    }

    public void addWithWeight(T element, int weight) {
        if (weight < 1) {
            return;
        }
        elements.merge(element, weight, Integer::sum);
        pool.add(element);
    }

    @Nullable
    @Override
    public T draw() {
        int allWeights = pool.stream().reduce(0, (partialResult, element) -> partialResult + elements.get(element), Integer::sum);
        if (allWeights < 1) {
            return null;
        }

        int targetWeight = random.nextInt(allWeights);
        int accumulatedWeight = 0;

        for (T element : pool) {
            accumulatedWeight += elements.get(element);
            if (targetWeight < accumulatedWeight) {
                pool.remove(element);
                return element;
            }
        }
        return null;
    }

    @Override
    public void reset() {
        pool = new HashSet<>(elements.keySet());
    }
}
