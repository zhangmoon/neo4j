/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.kernel.api;

import java.util.Map;
import java.util.Optional;

import org.neo4j.internal.kernel.api.exceptions.FrozenLocksException;
import org.neo4j.internal.kernel.api.exceptions.InvalidTransactionTypeKernelException;
import org.neo4j.internal.kernel.api.exceptions.TransactionFailureException;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * A transaction with the graph database.
 *
 * Access to the graph is performed via sub-interfaces like {@link Read}.
 * Changes made within a transaction are immediately visible to all operations within it, but are only
 * visible to other transactions after the successful commit of the transaction.
 *
 * <p>
 * Typical usage:
 * <pre>
 * try ( Transaction transaction = session.beginTransaction() )
 * {
 *      ...
 *      transaction.commit();
 * }
 * catch ( SomeException e )
 * {
 *      ...
 * }
 * </pre>
 *
 * <p>
 * Typical usage of {@code rollback()}, if failure isn't controlled with exceptions:
 * <pre>
 * try ( Transaction transaction = session.beginTransaction() )
 * {
 *      ...
 *      if ( ... some condition )
 *      {
 *          transaction.rollback();
 *      }
 *      else
 *      {
 *          transaction.commit();
 *      }
 * }
 * </pre>
 */
public interface Transaction extends AutoCloseable
{
    enum Type
    {
        implicit,
        explicit
    }

    /**
     * The store id of a rolled back transaction.
     */
    long ROLLBACK = -1;

    /**
     * The store id of a read-only transaction.
     */
    long READ_ONLY = 0;

    /**
     * Commit and any changes introduced as part of this transaction.
     * Any transaction that was not committed will be rolled back when it will be closed.
     *
     * When {@code commit()} is completed, all resources are released and no more changes are possible in this transaction.
     *
     * @return id of the committed transaction or {@link #ROLLBACK} if transaction was rolled back or
     * {@link #READ_ONLY} if transaction was read-only.
     */
    long commit() throws TransactionFailureException;

    /**
     * Roll back and any changes introduced as part of this transaction.
     *
     * When {@code rollback()} is completed, all resources are released and no more changes are possible in this transaction.
     */
    void rollback() throws TransactionFailureException;

    /**
     * @return The Read operations of the graph. The returned instance targets the active transaction state layer.
     */
    Read dataRead();

    /**
     * @return The Write operations of the graph. The returned instance writes to the active transaction state layer.
     * @throws InvalidTransactionTypeKernelException when transaction cannot be upgraded to a write transaction. This
     * can happen when there have been schema modifications.
     */
    Write dataWrite() throws InvalidTransactionTypeKernelException;

    /**
     * @return Token read operations
     */
    TokenRead tokenRead();

    /**
     * @return Token read operations
     */
    TokenWrite tokenWrite();

    /**
     * @return Token read and write operations
     */
    Token token();

    /**
     * @return The schema index read operations of the graph, used for finding indexes.
     */
    SchemaRead schemaRead();

    /**
     * @return The schema index write operations of the graph, used for creating and dropping indexes and constraints.
     */
    SchemaWrite schemaWrite() throws InvalidTransactionTypeKernelException;

    /**
     * @return The lock operations of the graph.
     */
    Locks locks();

    /**
     * Forbid acquisition and releasing of locks on this transaction. Any call through the kernel API that
     * requires a lock to be acquired or released will throw a {@link FrozenLocksException}. Calling `freezeLocks`
     * several times will nest the freezing.
     *
     * A transaction can be opened to new lock interactions again by calling {@link Transaction#thawLocks()}
     * once for every freeze.
     */
    void freezeLocks();

    /**
     * Allow acquisition and releasing of locks on this transaction. Thaws one nesting of {@link Transaction#freezeLocks()},
     * which restores the Transaction to normal operation if there has been the same number of freeze and thaw calls.
     */
    void thawLocks();

    /**
     * @return The cursor factory
     */
    CursorFactory cursors();

    /**
     * @return Returns procedure operations
     */
    Procedures procedures();

    /**
     * @return statistics about the execution
     */
    ExecutionStatistics executionStatistics();

    /**
     * Closes this transaction, roll back any changes if {@link #commit()} was not called.
     *
     * @return id of the committed transaction or {@link #ROLLBACK} if transaction was rolled back or
     * {@link #READ_ONLY} if transaction was read-only.
     */
    long closeTransaction() throws TransactionFailureException;

    /**
     * Closes this transaction, roll back any changes if {@link #commit()} was not called.
     */
    @Override
    default void close() throws TransactionFailureException
    {
        if ( isOpen() )
        {
            closeTransaction();
        }
    }

    /**
     * @return {@code true} if the transaction is still open, i.e. if {@link #close()} hasn't been called yet.
     */
    boolean isOpen();

    /**
     * @return {@link Status} if {@link #markForTermination(Status)} has been invoked, otherwise empty optional.
     */
    Optional<Status> getReasonIfTerminated();

    /**
     * @return true if transaction was terminated, otherwise false
     */
    boolean isTerminated();

    /**
     * Marks this transaction for termination, such that it cannot commit successfully and will try to be
     * terminated by having other methods throw a specific termination exception, as to sooner reach the assumed
     * point where {@link #close()} will be invoked.
     */
    void markForTermination( Status reason );

    /**
     * Sets the user defined meta data to be associated with started queries.
     * @param data the meta data
     */
    void setMetaData( Map<String,Object> data );

    /**
     * Gets associated meta data.
     *
     * @return the meta data
     */
    Map<String,Object> getMetaData();
}
