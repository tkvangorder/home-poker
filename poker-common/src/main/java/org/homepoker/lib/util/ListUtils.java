package org.homepoker.lib.util;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ListUtils {

	/**
	 * Applies a mapping function to each element in the list.
	 *
	 * @param ls  The list to modify.
	 * @param map The mapping function to apply to each element.
	 * @param <T> The type of elements in the list.
	 * @return A new list with modified elements, or the original list if unchanged.
	 */
	public static <T> List<T> map(List<T> ls, Function<T, @Nullable T> map) {
		if (ls.isEmpty()) {
			return ls;
		}

		List<@Nullable T> newLs = ls;
		boolean nullEncountered = false;
		for (int i = 0; i < ls.size(); i++) {
			T tree = ls.get(i);
			T newTree = map.apply(tree);
			if (newTree != tree) {
				if (newLs == ls) {
					newLs = new ArrayList<>(ls);
				}
				newLs.set(i, newTree);
			}
			nullEncountered |= newTree == null;
		}

		if (newLs != ls && nullEncountered) {
			newLs.removeIf(Objects::isNull);
		}

		//noinspection NullableProblems
		return newLs;
	}
}
