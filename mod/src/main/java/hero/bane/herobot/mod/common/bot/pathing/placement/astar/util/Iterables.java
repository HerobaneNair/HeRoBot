package hero.bane.herobot.mod.common.bot.pathing.placement.astar.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class Iterables {
  private Iterables() {
    throw new AssertionError("No instances");
  }

  public static int size(Iterable<?> iterable) {
    if (iterable instanceof Collection) {
      return ((Collection<?>) iterable).size();
    }
    int count = 0;
    for (Object element : iterable) {
      count++;
    }
    return count;
  }

  public static <T> T getLast(Iterable<T> iterable) {
    if (iterable instanceof List) {
      List<T> list = (List<T>) iterable;
      if (list.isEmpty()) {
        throw new NoSuchElementException();
      }
      return list.get(list.size() - 1);
    }

    Iterator<T> iterator = iterable.iterator();
    if (!iterator.hasNext()) {
      throw new NoSuchElementException();
    }

    T last = iterator.next();
    while (iterator.hasNext()) {
      last = iterator.next();
    }
    return last;
  }

  public static <T> Iterable<T> limit(final Iterable<T> iterable, final int limitSize) {
    if (limitSize < 0) {
      throw new IllegalArgumentException("limit is negative: " + limitSize);
    }

    return () ->
        new Iterator<T>() {
          private final Iterator<T> iterator = iterable.iterator();
          private int remaining = limitSize;

          @Override
          public boolean hasNext() {
            return remaining > 0 && iterator.hasNext();
          }

          @Override
          public T next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }
            remaining--;
            return iterator.next();
          }

          @Override
          public void remove() {
            iterator.remove();
          }
        };
  }

  public static <T> Iterable<T> concat(
      final Iterable<? extends T> a, final Iterable<? extends T> b) {
    return () ->
        new Iterator<T>() {
          private final Iterator<? extends T> iteratorA = a.iterator();
          private final Iterator<? extends T> iteratorB = b.iterator();

          @Override
          public boolean hasNext() {
            return iteratorA.hasNext() || iteratorB.hasNext();
          }

          @Override
          public T next() {
            if (iteratorA.hasNext()) {
              return iteratorA.next();
            }
            if (iteratorB.hasNext()) {
              return iteratorB.next();
            }
            throw new NoSuchElementException();
          }
        };
  }
}
