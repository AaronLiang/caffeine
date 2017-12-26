/*
 * Copyright 2015 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.jcache.processor;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.integration.CacheLoader;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;

/**
 * An entry that is consumed by an {@link EntryProcessor}. The updates to the entry are replayed
 * on the cache when the processor completes.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class EntryProcessorEntry<K, V> implements MutableEntry<K, V> {
  private final boolean hasEntry;
  private final K key;

  private V value;
  private Action action;
  private Optional<CacheLoader<K, V>> cacheLoader;

  public EntryProcessorEntry(K key, @Nullable V value, Optional<CacheLoader<K, V>> cacheLoader) {
    this.hasEntry = (value != null);
    this.cacheLoader = cacheLoader;
    this.action = Action.NONE;
    this.value = value;
    this.key = key;
  }

  @Override
  public boolean exists() {
    return (getValue() != null);
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public V getValue() {
    if (action != Action.NONE) {
      return value;
    } else if (value != null) {
      action = Action.READ;
    } else if (cacheLoader.isPresent()) {
      value = cacheLoader.get().load(key);
      cacheLoader = Optional.empty();
      if (value != null) {
        action = Action.LOADED;
      }
    }
    return value;
  }

  @Override
  public void remove() {
    action = (action == Action.CREATED) ? Action.NONE : Action.DELETED;
    value = null;
  }

  @Override
  public void setValue(V value) {
    requireNonNull(value);
    if (action != Action.CREATED) {
      action = (exists() && hasEntry) ? Action.UPDATED : Action.CREATED;
    }
    this.value = value;
  }

  /** @return the dominant action performed by the processor on the entry. */
  public Action getAction() {
    return action;
  }

  @Override
  public <T> T unwrap(Class<T> clazz) {
    if (!clazz.isInstance(this)) {
      throw new IllegalArgumentException("Class " + clazz + " is unknown to this implementation");
    }
    @SuppressWarnings("unchecked")
    T castedEntry = (T) this;
    return castedEntry;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Cache.Entry<?, ?>)) {
      return false;
    }
    Cache.Entry<?, ?> entry = (Cache.Entry<?, ?>) o;
    return Objects.equals(key, entry.getKey())
        && Objects.equals(getValue(), entry.getValue());
  }

  @Override
  public int hashCode() {
    return (key == null ? 0 : key.hashCode()) ^ (getValue() == null ? 0 : value.hashCode());
  }

  @Override
  public String toString() {
    return key + "=" + getValue();
  }
}
