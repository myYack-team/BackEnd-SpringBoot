package com.myyak.repository;

import com.myyak.domain.Food;
import com.myyak.domain.enums.FoodCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    Optional<Food> findByName(String name);

    List<Food> findByCategory(FoodCategory category);

    List<Food> findByNameIn(List<String> names);
}
