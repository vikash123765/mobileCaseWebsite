package com.vikash.mobileCaseBackend.repo;

import com.vikash.mobileCaseBackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IRepoUser extends JpaRepository<User,Integer> {

    User findByUserEmail(String newEmail);
}
