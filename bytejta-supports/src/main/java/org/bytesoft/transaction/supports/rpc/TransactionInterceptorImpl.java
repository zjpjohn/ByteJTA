/**
 * Copyright 2014-2016 yangming.liu<bytefox@126.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.transaction.supports.rpc;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;

import org.apache.log4j.Logger;
import org.bytesoft.bytejta.supports.resource.RemoteResourceDescriptor;
import org.bytesoft.bytejta.supports.wire.RemoteCoordinator;
import org.bytesoft.transaction.Transaction;
import org.bytesoft.transaction.TransactionBeanFactory;
import org.bytesoft.transaction.TransactionContext;
import org.bytesoft.transaction.TransactionManager;
import org.bytesoft.transaction.aware.TransactionBeanFactoryAware;
import org.bytesoft.transaction.internal.TransactionException;

public class TransactionInterceptorImpl implements TransactionInterceptor, TransactionBeanFactoryAware {
	static final Logger logger = Logger.getLogger(TransactionInterceptorImpl.class.getSimpleName());
	private TransactionBeanFactory transactionBeanFactory;

	public void beforeSendRequest(TransactionRequest request) throws IllegalStateException {
		TransactionManager transactionManager = this.transactionBeanFactory.getTransactionManager();
		Transaction transaction = transactionManager.getTransactionQuietly();
		if (transaction == null) {
			return;
		}

		TransactionContext srcTransactionContext = transaction.getTransactionContext();
		TransactionContext transactionContext = srcTransactionContext.clone();
		request.setTransactionContext(transactionContext);
		try {
			RemoteCoordinator resource = request.getTargetTransactionCoordinator();

			RemoteResourceDescriptor descriptor = new RemoteResourceDescriptor();
			descriptor.setDelegate(resource);
			descriptor.setIdentifier(resource.getIdentifier());

			transaction.enlistResource(descriptor);
		} catch (IllegalStateException ex) {
			logger.error("TransactionInterceptorImpl.beforeSendRequest(TransactionRequest)", ex);
			throw ex;
		} catch (RollbackException ex) {
			transaction.setRollbackOnlyQuietly();
			logger.error("TransactionInterceptorImpl.beforeSendRequest(TransactionRequest)", ex);
			throw new IllegalStateException(ex);
		} catch (SystemException ex) {
			logger.error("TransactionInterceptorImpl.beforeSendRequest(TransactionRequest)", ex);
			throw new IllegalStateException(ex);
		}
	}

	public void beforeSendResponse(TransactionResponse response) throws IllegalStateException {
		TransactionManager transactionManager = this.transactionBeanFactory.getTransactionManager();
		Transaction transaction = transactionManager.getTransactionQuietly();
		if (transaction == null) {
			return;
		}

		TransactionContext srcTransactionContext = transaction.getTransactionContext();
		TransactionContext transactionContext = srcTransactionContext.clone();
		RemoteCoordinator coordinator = this.transactionBeanFactory.getTransactionCoordinator();

		response.setTransactionContext(transactionContext);
		// response.setSourceTransactionCoordinator(coordinator);
		try {
			coordinator.end(transactionContext, XAResource.TMSUCCESS);
		} catch (TransactionException ex) {
			throw new IllegalStateException(ex);
		}

	}

	public void afterReceiveRequest(TransactionRequest request) throws IllegalStateException {
		TransactionContext srcTransactionContext = request.getTransactionContext();
		if (srcTransactionContext == null) {
			return;
		}

		TransactionContext transactionContext = srcTransactionContext.clone();
		RemoteCoordinator coordinator = this.transactionBeanFactory.getTransactionCoordinator();
		try {
			coordinator.start(transactionContext, XAResource.TMNOFLAGS);
		} catch (TransactionException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public void afterReceiveResponse(TransactionResponse response) throws IllegalStateException {
		TransactionManager transactionManager = this.transactionBeanFactory.getTransactionManager();
		Transaction transaction = transactionManager.getTransactionQuietly();
		TransactionContext transactionContext = response.getTransactionContext();
		RemoteCoordinator resource = response.getSourceTransactionCoordinator();
		if (transaction == null || transactionContext == null) {
			return;
		} else if (resource == null) {
			logger.error("TransactionInterceptorImpl.afterReceiveResponse(TransactionRequest): remote coordinator is null.");
			throw new IllegalStateException("remote coordinator is null.");
		}

		try {
			RemoteResourceDescriptor descriptor = new RemoteResourceDescriptor();
			descriptor.setDelegate(resource);
			descriptor.setIdentifier(resource.getIdentifier());

			transaction.delistResource(descriptor, XAResource.TMSUCCESS);
		} catch (IllegalStateException ex) {
			logger.error("TransactionInterceptorImpl.afterReceiveResponse(TransactionRequest)", ex);
			throw ex;
		} catch (SystemException ex) {
			logger.error("TransactionInterceptorImpl.afterReceiveResponse(TransactionRequest)", ex);
			throw new IllegalStateException(ex);
		}

	}

	public void setBeanFactory(TransactionBeanFactory tbf) {
		this.transactionBeanFactory = tbf;
	}

}
