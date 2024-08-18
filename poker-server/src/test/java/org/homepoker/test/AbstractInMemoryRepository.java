package org.homepoker.test;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.FluentQuery;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class AbstractInMemoryRepository<T, ID> implements MongoRepository<T, ID> {

  @Override
  public <S extends T> S insert(S entity) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> List<S> insert(Iterable<S> entities) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> Optional<S> findOne(Example<S> example) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> List<S> findAll(Example<S> example) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> long count(Example<S> example) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> boolean exists(Example<S> example) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> S save(S entity) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public <S extends T> List<S> saveAll(Iterable<S> entities) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public Optional<T> findById(ID id) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public boolean existsById(ID id) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public List<T> findAll() {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public List<T> findAllById(Iterable<ID> ids) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public long count() {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public void deleteById(ID id) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public void delete(T entity) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public void deleteAllById(Iterable<? extends ID> ids) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public void deleteAll(Iterable<? extends T> entities) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public void deleteAll() {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public List<T> findAll(Sort sort) {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public Page<T> findAll(Pageable pageable) {
    throw new UnsupportedOperationException("Not Implemented");
  }
}
