package com.bookphrase.domain.tag.repository;

import com.bookphrase.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByOrderByNameAsc();

    List<Tag> findByNameIn(List<String> names);
}
