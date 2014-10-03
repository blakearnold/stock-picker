package com.theblakearnold.stocksolver.model;

import java.util.ArrayList;
import java.util.List;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class CategoryGroupModel {

	CategoryGroupModel() {}

	public abstract String name();
	public abstract ImmutableList<CategoryModel> categories();

	public static Builder newBuilder() {
		return new Builder();
	}

	public static class Builder {
		private String name;
		private final List<CategoryModel> categories = new ArrayList<>();

		private Builder() {}

		public Builder setName(String name) {
			this.name = name;
			return this;
		}

		public Builder addCategory(CategoryModel category) {
			Preconditions.checkNotNull(category);
			categories.add(category);
			return this;
		}

		public CategoryGroupModel build() {
			if (name.isEmpty()) {
				throw new IllegalStateException("name not set");
			}

			if (categories.isEmpty()) {
				throw new IllegalStateException("no categories added");
      }
      return new AutoValue_CategoryGroupModel(name, ImmutableList.copyOf(categories));
    }
  }
}
