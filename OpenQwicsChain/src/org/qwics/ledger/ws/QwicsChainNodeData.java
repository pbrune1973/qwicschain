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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.qwics.ledger.Account;
import org.qwics.ledger.Asset;
import org.qwics.ledger.Node;
import org.qwics.ledger.NodeConfig;
import org.qwics.ledger.Transaction;
import org.qwics.ledger.TransactionId;
import org.qwics.ledger.connector.QwicsChainConnectorManager;
import org.qwics.ledger.contract.Contract;
import org.qwics.ledger.contract.CreateContractTransaction;
import org.qwics.ledger.contract.QwicsChainContractManager;

import de.flexiprovider.core.FlexiCoreProvider;
import de.flexiprovider.ec.FlexiECProvider;
import de.flexiprovider.ec.parameters.CurveParams;
import de.flexiprovider.ec.parameters.CurveRegistry.BrainpoolP160r1;
import util.hash.MurmurHash3;

@Singleton
@LocalBean
public class QwicsChainNodeData implements Comparator<Transaction> {
	@PersistenceContext(unitName = "QwicsDB")
	private EntityManager em;

	// BEGIN Contract Support
	@EJB
	QwicsChainContractManager contractManager;
	// END Contract Support
	@EJB
	QwicsChainConnectorManager connectorManager;

	private PrivateKey nodePrivKey;
	private PublicKey nodePubKey;
	private String hostname;

	private HashMap<TransactionId, Transaction> candidates;
	private NodeConfig config = null;
	private boolean startConsensusProtocol = false;

	private byte[] genesisPubKey = new byte[] { (byte) 0x30, (byte) 0x42, (byte) 0x30, (byte) 0x14, (byte) 0x6,
			(byte) 0x7, (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0xce, (byte) 0x3d, (byte) 0x2, (byte) 0x1,
			(byte) 0x6, (byte) 0x9, (byte) 0x2b, (byte) 0x24, (byte) 0x3, (byte) 0x3, (byte) 0x2, (byte) 0x8,
			(byte) 0x1, (byte) 0x1, (byte) 0x1, (byte) 0x3, (byte) 0x2a, (byte) 0x0, (byte) 0x4, (byte) 0xb5,
			(byte) 0xf0, (byte) 0x49, (byte) 0xb0, (byte) 0x95, (byte) 0xa9, (byte) 0xdb, (byte) 0x45, (byte) 0x33,
			(byte) 0xf, (byte) 0x43, (byte) 0xd6, (byte) 0xf1, (byte) 0x92, (byte) 0x99, (byte) 0x9a, (byte) 0x6f,
			(byte) 0x34, (byte) 0x6d, (byte) 0x60, (byte) 0x3a, (byte) 0x0, (byte) 0x2d, (byte) 0x9, (byte) 0xd7,
			(byte) 0xe7, (byte) 0x4f, (byte) 0xcf, (byte) 0xd9, (byte) 0xcf, (byte) 0xa8, (byte) 0x6f, (byte) 0xce,
			(byte) 0xac, (byte) 0x24, (byte) 0x88, (byte) 0xda, (byte) 0x6f, (byte) 0x29, (byte) 0x8 };

	public QwicsChainNodeData() {
		hostname = System.getProperty("org.qwics.ledger.host");
		if (hostname == null) {
			hostname = "localhost";
			try {
				hostname = InetAddress.getLocalHost().getHostAddress();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.err.println("Hostname: " + hostname);
		candidates = new HashMap<TransactionId, Transaction>();
	}

	public Transaction loadTransaction(TransactionId transId) {
		synchronized (config) {
			for (Node n : config.getKnownNodes()) {
				if (n.isTrustworthy()) {
					try {
						Client client = ClientBuilder.newClient();
						Response response = client.target("http://" + n.getHostname() + ":" + n.getPort()
								+ "/QwicsChain/services/api/getTransaction/" + transId.getAccountId() + "/"
								+ transId.getTransId()).request().get();
						Transaction ta = response.readEntity(Transaction.class);
						return ta;
					} catch (Exception e) {
					}
				}
			}
		}
		return null;
	}

	public Account loadAccount(long accountId) {
		synchronized (config) {
			for (Node n : config.getKnownNodes()) {
				if (n.isTrustworthy()) {
					try {
						Client client = ClientBuilder.newClient();
						Response response = client.target("http://" + n.getHostname() + ":" + n.getPort()
								+ "/QwicsChain/services/api/getAccount/" + accountId).request().get();
						Account a = response.readEntity(Account.class);
						return a;
					} catch (Exception e) {
					}
				}
			}
		}
		return null;
	}

	@Lock(LockType.READ)
	public Node getNode() {
		Node n = new Node();
		n.setHostname(hostname);
		n.setPort(9080);
		n.setNodePubKey(config.getPubKey());
		return n;
	}

	@Lock(LockType.READ)
	public Node getKnownNode(String hostname, int port) {
		Client client = ClientBuilder.newClient();
		try {
			Response response = client.target("http://" + hostname + ":" + port + "/QwicsChain/services/api/getNode")
					.request().get();
			Node n = response.readEntity(Node.class);
			response = client.target("http://" + hostname + ":" + port + "/QwicsChain/services/api/addKnownNode")
					.request().post(Entity.entity(getNode(), MediaType.APPLICATION_JSON));
			boolean r = Boolean.parseBoolean(response.readEntity(String.class));
			if (r) {
				return n;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new QwicsChainException("Initial Download-Node not reachable!");
	}

	public void setUpNode(String password, String hostname, int port) {
		try {
			Security.addProvider(new FlexiCoreProvider());
			Security.addProvider(new FlexiECProvider());

			SecretKeyFactory kf = SecretKeyFactory.getInstance("PBEWithMD5AndTripleDES");
			KeySpec ks = new PBEKeySpec(password.toCharArray());
			SecretKey key = kf.generateSecret(ks);

			hostname = hostname.trim();

			config = em.find(NodeConfig.class, 1L);
			if (config == null) {
				config = new NodeConfig();
				try {
					KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECIES", "FlexiEC");
					CurveParams ecParams = new BrainpoolP160r1();
					kpg.initialize(ecParams);
					KeyPair keyPair = kpg.generateKeyPair();
					nodePubKey = keyPair.getPublic();
					nodePrivKey = keyPair.getPrivate();
					config.setPrivateKey(nodePrivKey, key);
					config.setPubKey(nodePubKey.getEncoded());

					if (hostname.length() > 0) {
						synchronized (config) {
							config.getKnownNodes().add(getKnownNode(hostname, port));
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				em.persist(config);
			} else {
				nodePubKey = config.getPublicKey();
				nodePrivKey = config.getPrivateKey(key);
				try {
					if (hostname.length() > 0) {
						addKnownNode(getKnownNode(hostname, port));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			// Set up genesis account and transaction
			Account gacc = em.find(Account.class, 0L);
			if (gacc == null) {
				gacc = loadAccount(0L);
				if (gacc == null) {
					gacc = new Account();
					gacc.setAccountId(0L);
					gacc.setCoins(new BigDecimal("1000000000000.0"));
					gacc.setPubKey(genesisPubKey);
				}
				em.persist(gacc);
			}

			Transaction gta = em.find(Transaction.class, new TransactionId(0, 0));
			if (gta == null) {
				gta = loadTransaction(new TransactionId(0, 0));
				if (gta == null) {
					gta = new Transaction();
					gta.setTransactionId(new TransactionId(0, 0));
					gta.setPrevTransactionId(new TransactionId(0, 0));
					gta.setCoins(new BigDecimal("1000000000000.0"));
					gta.setData(genesisPubKey);
					gta.setAction(Transaction.ACTION_TRANSFERCOINS);
					gta.setReceiverAccountId(0);
					gta.setValidated(true);
					gta.setBookingDate(new Date());
					gta.setAsset(new Asset(Asset.EMPTY, "", new BigDecimal(0.0), 0, null));
					gta.setHash(gta.getSecureHash());
				}
				em.persist(gta);
			}

			loadLedger();
			startConsensusProtocol = true;
		} catch (Exception e) {
			e.printStackTrace();
			throw new QwicsChainException(e);
		}
	}

	public void addKnownNode(Node node) {
		synchronized (config) {
			Node n = em.find(Node.class, node.getHostname());
			if (n == null) {
				em.persist(node);
			} else {
				node = n;
			}
			boolean contains = false;
			for (Node no : config.getKnownNodes()) {
				if (no.getHostname().equals(node.getHostname())) {
					contains = true;
					break;
				}
			}
			if (!contains) {
				config.getKnownNodes().add(node);
				em.merge(config);
			}
		}
	}

	@Lock(LockType.READ)
	public void addCandidateTransaction(Transaction ta) {
		synchronized (candidates) {
			candidates.put(ta.getTransactionId(), ta);
		}
	}

	@Lock(LockType.READ)
	public boolean checkTransaction(long accountId, long transId, byte[] hash) {
		try {
			Transaction ta = null;
			synchronized (candidates) {
				Set<TransactionId> ids = candidates.keySet();
				for (TransactionId tId : ids) {
					if ((tId.getAccountId() == accountId) && (tId.getTransId() == transId)) {
						ta = candidates.get(tId);
						break;
					}
				}
			}

			if (ta == null) {
				// If not in array anymore, it is already booked
				return true;
			}
			
			if (Arrays.equals(ta.getSecureHash(),hash)) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	@Lock(LockType.READ)
	public int compare(Transaction ta1, Transaction ta2) {
		TransactionId id1 = ta1.getPrevTransactionId();
		TransactionId id2 = ta2.getPrevTransactionId();
		long d = id1.getAccountId() - id2.getAccountId();
		if (d == 0) {
			return (int) (id1.getTransId() - id2.getTransId());
		} else {
			return (int) d;
		}
	}

	public long getLastTransactionId(long accountId) {
		Query q = em.createNativeQuery(
				"SELECT MAX(t.trans_id) FROM Transaction t WHERE t.account_id = ?1 GROUP BY t.account_id");
		q.setParameter(1, accountId);
		List<BigInteger> ids = q.getResultList();
		return ids.get(0).longValue();
	}

	public void confirmTransaction(Transaction ta) {
		// Add ta to the blockchain
		try {
			Transaction prev = em.find(Transaction.class, ta.getPrevTransactionId());
			ta.setPrevHash(prev.getHash());
			ta.setHash(ta.getSecureHash());
			ta.setValidated(true);
			ta.setBookingDate(new Date());
			em.merge(ta);

			Account a = em.find(Account.class, ta.getTransactionId().getAccountId());
			// Pay transaction fee
			a.setCoins(a.getCoins().subtract(new BigDecimal(0.01)));
			Account ga = em.find(Account.class, 0L);
			ga.setCoins(ga.getCoins().add(new BigDecimal(0.01)));
			em.merge(ga);

			if (ta.getAction() == Transaction.ACTION_CREATEASSET) {
				int i = a.indexOf(ta.getAsset());
				if (i < 0) {
					a.getAssets().add(ta.getAsset());
				} else {
					a.getAssets().get(i).setAmount(a.getAssets().get(i).getAmount().add(ta.getAsset().getAmount()));
				}
			}
			if (ta.getAction() == Transaction.ACTION_TRANSFERASSET) {
				Account rec = em.find(Account.class, ta.getReceiverAccountId());
				int i = a.indexOf(ta.getAsset());
				if (i < 0) {
					throw new Exception("Missing asset!");
				}
				a.getAssets().get(i).setAmount(a.getAssets().get(i).getAmount().subtract(ta.getAsset().getAmount()));
				if (a.getAssets().get(i).getAmount().compareTo(new BigDecimal(0.0)) <= 0) {
					a.getAssets().remove(ta.getAsset());
				}
				i = rec.indexOf(ta.getAsset());
				if (i < 0) {
					rec.getAssets().add(ta.getAsset());
				} else {
					rec.getAssets().get(i).setAmount(rec.getAssets().get(i).getAmount().add(ta.getAsset().getAmount()));
				}
				em.merge(rec);
			}
			// BEGIN Contract Support
			if (ta instanceof CreateContractTransaction) {
				contractManager.putContractAccount((CreateContractTransaction) ta);
				a.setCoins(a.getCoins().subtract(ta.getCoins()));
			} else
			// END Contract Support
			if (ta.getAction() == Transaction.ACTION_TRANSFERCOINS) {
				Account rec = em.find(Account.class, ta.getReceiverAccountId());
				if (rec == null) {
					// Create new account
					rec = new Account();
					rec.setAccountId(ta.getReceiverAccountId());
					rec.setCoins(ta.getCoins());
					rec.setPubKey(ta.getData());
					em.persist(rec);

					// Create genesis transaction for new account
					Transaction gta = new Transaction();
					gta.setTransactionId(new TransactionId(ta.getReceiverAccountId(), 0));
					gta.setPrevTransactionId(new TransactionId(ta.getReceiverAccountId(), 0));
					gta.setCoins(ta.getCoins());
					gta.setData(ta.getData());
					gta.setAction(Transaction.ACTION_TRANSFERCOINS);
					gta.setReceiverAccountId(ta.getReceiverAccountId());
					gta.setValidated(true);
					gta.setBookingDate(new Date());
					gta.setAsset(new Asset(Asset.EMPTY, "", new BigDecimal(0.0), 0, null));
					gta.setPrevHash(ta.getHash());
					gta.setHash(gta.getSecureHash());
					em.persist(gta);
				} else {
					rec.setCoins(rec.getCoins().add(ta.getCoins()));
					em.merge(rec);
				}
				a.setCoins(a.getCoins().subtract(ta.getCoins()));
			}
			em.merge(a);

			// BEGIN Contract Support
			contractManager.receive(ta);
			// END Contract Support
			connectorManager.receive(ta);
		} catch (Exception e) {
			throw new QwicsChainException(e);
		}
	}

	@Schedule(hour = "*", minute = "*", second = "*/10", persistent = false)
	public void loadOpenTransactions() {
		System.err.println("loadOpenTransactions BEGIN");
		if (!startConsensusProtocol) {
			return;
		}

		// Update candidate set with still open transactions
		Query q = em.createQuery("SELECT t FROM Transaction t WHERE t.validated = false AND t.candidate = false");
		List<Transaction> openTa = q.getResultList();
		System.err.println("Loaded possible candidate transactions: " + openTa.size());

		for (Transaction t : openTa) {
			validateTransaction(t);
		}
		System.err.println("loadOpenTransactions END");
	}

	@Schedule(hour = "*", minute = "*", second = "5/10", persistent = false)
	@Lock(LockType.READ)
	public void checkConsensus() {
		System.err.println("checkConsensus BEGIN");
		if (!startConsensusProtocol) {
			return;
		}

		// Process candidate set
		int len = 0;
		TransactionId candIds[] = null;
		synchronized (candidates) {
			Set<TransactionId> ids = candidates.keySet();
			len = ids.size();
			candIds = new TransactionId[len];
			candIds = ids.toArray(candIds);
		}
		System.err.println("checkConsensus " + len);
		Jsonb jsonb = JsonbBuilder.create();
		for (int i = 0; i < len; i++) {
			int voters = 0;
			int votes = 0;
			try {
				Client client = ClientBuilder.newClient();
				boolean nodeDeleted = false;
				synchronized (config) {
					for (Iterator<Node> it = config.getKnownNodes().iterator(); it.hasNext();) {
						Node n = it.next();
						voters++;
						try {
							Transaction ta = null;
							synchronized (candidates) {
								ta = candidates.get(candIds[i]);
							}
							// System.err.println("http://" + n.getHostname() + ":" + n.getPort()
							//		+ "/QwicsChain/services/api/checkTransaction/" + candIds[i].getAccountId() + "/"
							//		+ candIds[i].getTransId() + "/"
							//		+ java.net.URLEncoder.encode(jsonb.toJson(ta.getSecureHash())));

							Response response = client
									.target("http://" + n.getHostname() + ":" + n.getPort()
											+ "/QwicsChain/services/api/checkTransaction/" + candIds[i].getAccountId()
											+ "/" + candIds[i].getTransId() + "/"
											+ java.net.URLEncoder.encode(jsonb.toJson(ta.getSecureHash())))
									.request().get();
							boolean r = Boolean.parseBoolean(response.readEntity(String.class));
							if (r) {
								votes++;
							}
						} catch (Exception e) {
							e.printStackTrace();
							it.remove();
							nodeDeleted = true;
						}
					}
					if (nodeDeleted) {
						em.merge(config);
					}
				}
				client.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			if ((voters == 0) || ((voters > 0) && (((double) votes / (double) voters) >= 0.8))) {
				Transaction ta = em.find(Transaction.class, candIds[i]);
				confirmTransaction(ta);
				synchronized (candidates) {
					candidates.remove(candIds[i]);
				}
			}
		}
		System.err.println("checkConsensus END");
	}

	public boolean isInitiallyValid(Transaction ta) {
		// System.err.println("isInitiallyValid " + ta.getTransactionId());
		if ((ta.getAsset() == null) || (ta.getTransactionId() == null) || (ta.getPrevTransactionId() == null)) {
			return false;
		}

		if (ta.getTransactionId().getTransId() <= ta.getPrevTransactionId().getTransId()) {
			return false;
		}

		if ((ta.getAction() != Transaction.ACTION_CREATEASSET) && (ta.getAction() != Transaction.ACTION_TRANSFERASSET)
				&& (ta.getAction() != Transaction.ACTION_TRANSFERCOINS)) {
			return false;
		}

		if (ta.getBookingDate() != null) {
			return false;
		}

		try {
			Account acc = em.find(Account.class, ta.getTransactionId().getAccountId());
			if (acc == null) {
				// Try to download account data if it is missing
				acc = loadAccount(ta.getTransactionId().getAccountId());
				if (acc != null) {
					em.persist(acc);
				}
			}

			if ((acc != null) && (acc.getCoins().compareTo(new BigDecimal(0.01)) >= 0)) {
				KeyFactory keyFactory = KeyFactory.getInstance("ECIES", "FlexiEC");
				PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(acc.getPubKey()));
				if (!ta.verify(pubKey)) {
					return false;
				}
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		// BEGIN Contract Support
		if (ta instanceof CreateContractTransaction) {
			try {
				// Check if contract compiles
				CreateContractTransaction cta = (CreateContractTransaction) ta;
				Contract c = new Contract();
				c.init(cta.getReceiverAccountId(), cta.getSourceCode());
			} catch (Exception e) {
				return false;
			}
		}
		// END Contract Support

		return true;
	}

	public boolean isValid(Transaction ta) {
		if (ta == null) {
			return false;
		}

		if (ta.isTainted()) {
			return false;
		}

		if (!isInitiallyValid(ta)) {
			return false;
		}

		// System.err.println("isValid " + ta.getTransactionId() + " " + ta.getPrevTransactionId());

		// If ta not yet arrived and is not persistent, it is not valid
		Transaction t = em.find(Transaction.class, ta.getTransactionId());
		if (t == null) {
			return false;
		} else {
			if (t.isValidated()) {
				// Transaction validated and booked already, no further check necessary
				return true;
			}
			try {
				// Transactions are different, someone tries to do something wrong
				if (!Arrays.equals(t.getSecureHash(), ta.getSecureHash())) {
					return false;
				}
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		// Check, if predecessor exists
		t = em.find(Transaction.class, ta.getPrevTransactionId());
		if (t == null) {
			// Try to download predecessor
			t = loadTransaction(ta.getPrevTransactionId());
			if (t == null) {
				return false;
			} else {
				em.persist(t);
			}
		}

		// Check, if no other transaction with same predecessor exists
		Query q = em.createQuery("SELECT t FROM Transaction t WHERE t.prevTransactionId = :ta");
		q.setParameter("ta", ta.getPrevTransactionId());
		int n = q.getResultList().size();
		if (n > 1) {
			// Check if first transaction of an account
			if (!((ta.getPrevTransactionId().getTransId() == 0) && (n == 2))) {
				return false;
			}
		}

		// Create only assets that are issued by yourself
		if (ta.getAction() == Transaction.ACTION_CREATEASSET) {
			Account a = em.find(Account.class, ta.getTransactionId().getAccountId());
			if (ta.getAsset().getIssuerAccountId() != ta.getTransactionId().getAccountId()) {
				return false;
			}
			try {
				if (!ta.getAsset().verify(a.retrievePublicKey())) {
					return false;
				}
			} catch (Exception e) {
				return false;
			}
		}

		// Do not transfer an asset you do not have or to a non-existing account
		if (ta.getAction() == Transaction.ACTION_TRANSFERASSET) {
			Account a = em.find(Account.class, ta.getTransactionId().getAccountId());
			int i = a.indexOf(ta.getAsset());
			if (i < 0) {
				return false;
			}
			if (a.getAssets().get(i).getAmount().compareTo(ta.getAsset().getAmount()) < 0) {
				return false;
			}
			a = em.find(Account.class, ta.getReceiverAccountId());
			if (a == null) {
				return false;
			}
		}

		if (ta.getAction() == Transaction.ACTION_TRANSFERCOINS) {
			Account a = em.find(Account.class, ta.getTransactionId().getAccountId());
			if (a.getCoins().compareTo(ta.getCoins().add(new BigDecimal(0.01))) < 0) {
				return false;
			}
			a = em.find(Account.class, ta.getReceiverAccountId());
			if (a == null) {
				if (ta.getData() == null) {
					// Pubkey for new account not included
					return false;
				} else {
					// Check if valid pubkey
					try {
						KeyFactory keyFactory = KeyFactory.getInstance("ECIES", "FlexiEC");
						keyFactory.generatePublic(new X509EncodedKeySpec(ta.getData()));

						long id = 0L | MurmurHash3.murmurhash3_x86_32(ta.getData(), 0, ta.getData().length, 33);
						if (ta.getReceiverAccountId() != id) {
							return false;
						}
					} catch (Exception e) {
						e.printStackTrace();
						return false;
					}
				}
			}
		}

		return true;
	}

	public void validateTransaction(Transaction ta) {
		if (isValid(ta)) {
			ta.setCandidate(true);
			em.merge(ta);
			addCandidateTransaction(ta);
		}
	}

	public void signByNode(Transaction ta) {
		try {
			ta.getNodeSignatures().put(hostname, ta.getSignature(nodePrivKey));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Asynchronous
	public void distributeCandidate(Transaction ta) {
		/*
		 * Send candidate to all known nodes which did not have it so far. This is
		 * equivalent to sending the signed message to other lieutenants in Lamport's
		 * original paper on the Byzantine Generals problem.
		 */
		try {
			Client client = ClientBuilder.newClient();
			boolean nodeDeleted = false;
			synchronized (config) {
				for (Iterator<Node> it = config.getKnownNodes().iterator(); it.hasNext();) {
					Node n = it.next();
					System.err.println("distributeCandidate to " + n.getHostname());
					if (ta.getNodeSignatures().get(n.getHostname()) == null) {
						// Node does not have candidate already
						try {
							String method = "addTransaction";
							// BEGIN Contract Support
							if (ta instanceof CreateContractTransaction) {
								method = "addCreateContractTransaction";
							}
							// END Contract Support

							Response response = client
									.target("http://" + n.getHostname() + ":" + n.getPort()
											+ "/QwicsChain/services/api/" + method)
									.request().post(Entity.entity(ta, MediaType.APPLICATION_JSON));
							boolean r = Boolean.parseBoolean(response.readEntity(String.class));
						} catch (Exception e) {
							e.printStackTrace();
							it.remove();
							nodeDeleted = true;
						}
					}
				}
				if (nodeDeleted) {
					em.merge(config);
				}
			}
			client.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Lock(LockType.READ)
	public Node getKnownNode(String hostname) {
		synchronized (config) {
			for (Node n : config.getKnownNodes()) {
				if (n.getHostname().equals(hostname)) {
					return n;
				}
			}
		}
		return null;
	}
	
	@Lock(LockType.READ)
	public List<Long> loadAccountIds() {
		synchronized (config) {
			for (Node n : config.getKnownNodes()) {
				if (n.isTrustworthy()) {
					try {
						Client client = ClientBuilder.newClient();
						Response response = client.target("http://" + n.getHostname() + ":" + n.getPort()
								+ "/QwicsChain/services/api/getAccountIds").request().get();
						List accountIds = response.readEntity(List.class);
						for (int i = 0; i < accountIds.size(); i++) {
							if (accountIds.get(i) instanceof BigDecimal) {
								accountIds.set(i, ((BigDecimal) accountIds.get(i)).longValue());
							}
						}
						return accountIds;
					} catch (Exception e) {
					}
				}
			}
		}
		return null;
	}
	
	@Lock(LockType.READ)
	public List<Long> loadTransIds(long accountId) {
		synchronized (config) {
			for (Node n : config.getKnownNodes()) {
				if (n.isTrustworthy()) {
					try {
						Client client = ClientBuilder.newClient();
						Response response = client.target("http://" + n.getHostname() + ":" + n.getPort()
								+ "/QwicsChain/services/api/getTransIds/" + accountId).request().get();
						List transIds = response.readEntity(List.class);
						for (int i = 0; i < transIds.size(); i++) {
							if (transIds.get(i) instanceof BigDecimal) {
								transIds.set(i, ((BigDecimal) transIds.get(i)).longValue());
							}
						}
						return transIds;
					} catch (Exception e) {
					}
				}
			}
		}
		return null;
	}

	@Asynchronous
	public void loadLedger() {
		try {
			List<Long> accountIds = loadAccountIds();
			if (accountIds != null) {
				for (int i = 0; i < accountIds.size(); i++) {
					Account aNew = loadAccount(accountIds.get(i));
					if (aNew != null) {
						Account a = em.find(Account.class, accountIds.get(i));
						if (a == null) {
							em.persist(aNew);
						} else {
							a.setCoins(aNew.getCoins());
							if (aNew.getAssets() != null) {
								a.setAssets(aNew.getAssets());
							}
							em.merge(a);
						}
					}

					List<Long> transIds = loadTransIds(accountIds.get(i));
					if (transIds != null) {
						for (int j = 0; j < transIds.size(); j++) {
							Transaction ta = em.find(Transaction.class,
									new TransactionId(accountIds.get(i), transIds.get(j)));
							if ((ta == null) || !ta.isValidated()) {
								Transaction taNew = loadTransaction(
										new TransactionId(accountIds.get(i), transIds.get(j)));
								if (taNew != null) {
									if (ta == null) {
										em.persist(taNew);
									} else {
										em.merge(taNew);
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new QwicsChainException(e);
		} finally {
			try {
				// Initialize candidate set with previouslyl open transactions
				Query q = em
						.createQuery("SELECT t FROM Transaction t WHERE t.validated = false AND t.candidate = true");
				List<Transaction> openTa = q.getResultList();
				System.err.println("Loaded existing candidate transactions: " + openTa.size());

				for (Transaction t : openTa) {
					addCandidateTransaction(t);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}
