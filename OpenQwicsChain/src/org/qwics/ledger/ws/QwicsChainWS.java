/*
QWICSchain Enterprise Blockchain framework for Java EE

Copyright (c) 2019 Philipp Brune    Email: Philipp.Brune@qwics.org

QWICSchain is free software. You can redistribute it and/or modify it under the  
terms of the GNU General Public License as published by the Free Software Foundation, 
either version 3 of the License, or (at your option) any later version.               

It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or F
ITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more 
details.
                                                                                      
You should have received a copy of the GNU General Public License 
along with this project. If not, see <http://www.gnu.org/licenses/>. 

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of the software nor the names of its contributors may not be
used to endorse or promote products derived from this software without specific
prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS  AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
*/

package org.qwics.ledger.ws;

import java.util.Arrays;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.qwics.ledger.Account;
import org.qwics.ledger.Node;
import org.qwics.ledger.Transaction;
import org.qwics.ledger.TransactionId;
import org.qwics.ledger.contract.CreateContractTransaction;

@Stateless
@LocalBean
@Path("api")
public class QwicsChainWS {
	@PersistenceContext(unitName = "QwicsDB")
	private EntityManager em;

	@EJB
	private QwicsChainNodeData nodeData;

	@POST
	@Path("setUpNode/{password}/{hostname}/{port}/{secret}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public boolean setUpNode(@PathParam("password") String password, @PathParam("hostname") String hostname,
			@PathParam("port") int port, @PathParam("secret") String secret) {
		String sec = System.getProperty("org.qwics.ledger.secret");
		if (sec == null) {
			sec = "";
		}
		if (!sec.equals(secret)) {
			return false;			
		}
		try {
			nodeData.setUpNode(password, hostname, port);
			return true;
		} catch (Exception e) {
			throw new QwicsChainException(e);
		}
	}

	@POST
	@Path("addKnownNode")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public boolean addKnownNode(String no) {
		try {
			Jsonb jsonb = JsonbBuilder.create();
			Node node = jsonb.fromJson(no, Node.class);
			nodeData.addKnownNode(node);
			return true;
		} catch (Exception e) {
			throw new QwicsChainException(e);
		}
	}

	@GET
	@Path("getNode")
	@Produces(MediaType.APPLICATION_JSON)
	public Node getNode() {
		try {
			return nodeData.getNode();
		} catch (Exception e) {
			throw new QwicsChainException(e);
		}
	}

	@POST
	@Path("addTransaction")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Transaction addTransaction(String ta) {
		try {
			Jsonb jsonb = JsonbBuilder.create();
			Transaction transaction = jsonb.fromJson(ta, Transaction.class);
			transaction.setValidated(false);
			transaction.setTainted(false);
			// System.err.println("addTranscation: "+ta);
			if (!nodeData.isInitiallyValid(transaction)) {
				return null;
			}

			Transaction t = em.find(Transaction.class, transaction.getTransactionId());
			if (t == null) {
				nodeData.signByNode(transaction);
				em.persist(transaction);
			} else {
				if (!t.isValidated() && !Arrays.equals(t.getSecureHash(), transaction.getSecureHash())) {
					// Someone tries to send two different transactions with the same id
					nodeData.signByNode(transaction);
					t.setTainted(true);
					em.merge(t);
				} else {
					return transaction;
				}
			}

			nodeData.distributeCandidate(transaction);
			return transaction;
		} catch (Exception e) {
			throw new QwicsChainException(e);
		}
	}

	// BEGIN Contract Support
	@POST
	@Path("addCreateContractTransaction")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Transaction addCreateContractTransaction(String ta) {
		try {
			Jsonb jsonb = JsonbBuilder.create();
			CreateContractTransaction transaction = jsonb.fromJson(ta, CreateContractTransaction.class);
			transaction.setValidated(false);
			transaction.setTainted(false);
			 // System.err.println("addCreateContractTranscation: " + ta);

			if (!nodeData.isInitiallyValid(transaction)) {
				return null;
			}

			CreateContractTransaction t = em.find(CreateContractTransaction.class, transaction.getTransactionId());
			if (t == null) {
				nodeData.signByNode(transaction);
				em.persist(transaction);
			} else {
				if (!t.isValidated() && !Arrays.equals(t.getSecureHash(), transaction.getSecureHash())) {
					// Someone tries to send two different transactions with the same id
					nodeData.signByNode(transaction);
					t.setTainted(true);
					em.merge(t);
				} else {
					return transaction;
				}
			}

			nodeData.distributeCandidate(transaction);
			return transaction;
		} catch (Exception e) {
			throw new QwicsChainException(e);
		}
	}
	// END Contract Support

	@GET
	@Path("getAccountIds")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Long> getAccountIds() {
		Query q = em.createNativeQuery("SELECT a.account_id FROM Account a");
		List<Long> ids = q.getResultList();
		return ids;
	}

	@GET
	@Path("getTransIds/{accountId}")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Long> getTransIds(@PathParam("accountId") long accountId) {
		Query q = em.createNativeQuery(
				"SELECT t.trans_id FROM Transaction t WHERE t.account_id = ?1 ORDER BY t.trans_id ASC");
		q.setParameter(1, accountId);
		List<Long> ids = q.getResultList();
		return ids;
	}

	@GET
	@Path("getAccount/{accountId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Account getAccount(@PathParam("accountId") long accountId) {
		return em.find(Account.class, accountId);
	}

	@GET
	@Path("getTransaction/{accountId}/{transId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Transaction getTransaction(@PathParam("accountId") long accountId, @PathParam("transId") long transId) {
		return em.find(Transaction.class, new TransactionId(accountId, transId));
	}

	@GET
	@Path("checkTransaction/{accountId}/{transId}/{hash}")
	@Produces(MediaType.APPLICATION_JSON)
	public boolean checkTransaction(@PathParam("accountId") long accountId, @PathParam("transId") long transId, @PathParam("hash") String hash) {
		try {
			Jsonb jsonb = JsonbBuilder.create();
			byte[] h = jsonb.fromJson(hash, byte[].class);
			// System.err.println(jsonb.toJson(h));
			
			Transaction t = getTransaction(accountId, transId);
			if (t == null) {
				return false;
			} else {
				if (!t.isCandidate()) {
					return false;
				}
			}
			
			return nodeData.checkTransaction(accountId, transId, h);			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@GET
	@Path("getLastTransId/{accountId}")
	@Produces(MediaType.APPLICATION_JSON)
	public long getLastTransId(@PathParam("accountId") long accountId) {
		return nodeData.getLastTransactionId(accountId);
	}

	@GET
	@Path("getBookedTransactions/{accountId}")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Transaction> getBookedTransactions(@PathParam("accountId") long accountId) {
		Query q = em.createQuery("SELECT t FROM Transaction t WHERE (t.transactionId.accountId = :a OR t.receiverAccountId = :b) AND t.validated = true ORDER BY t.bookingDate DESC");
		q.setParameter("a", accountId);
		q.setParameter("b", accountId);
		return q.getResultList();
	}

/*
	@GET
	@Path("invokeTaHandler")
	@Produces(MediaType.APPLICATION_JSON)
	public void invokeTaHandler() {
		try {
			InitialContext ctx = new InitialContext();
			TransactionHandler handler = (TransactionHandler) ctx
					.lookup("java:global/QwicsEJBApp/QwicsTaHandler!org.qwics.ledger.connector.TransactionHandler");
			handler.processTransaction(1, 2, 1, new BigDecimal(17), null, null);
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
*/
}
