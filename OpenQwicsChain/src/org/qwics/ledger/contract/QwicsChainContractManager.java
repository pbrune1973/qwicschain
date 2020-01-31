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

package org.qwics.ledger.contract;

import java.io.Serializable;
import java.math.BigDecimal;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.qwics.ledger.Account;
import org.qwics.ledger.Asset;
import org.qwics.ledger.Transaction;
import org.qwics.ledger.TransactionId;
import org.qwics.ledger.ws.QwicsChainException;
import org.qwics.ledger.ws.QwicsChainNodeData;

import util.hash.MurmurHash3;

@Singleton
@LocalBean
public class QwicsChainContractManager implements ContractIOProvider {
	@PersistenceContext(unitName = "QwicsDB")
	private EntityManager em;
	private HashMap<Long, Contract> contracts = new HashMap<Long, Contract>();
	@EJB
	QwicsChainNodeData nodeData;

	public QwicsChainContractManager() {
	}

	@PostConstruct
	public void init() {
		Query q = em.createQuery("SELECT ca FROM ContractAccount ca");
		List<ContractAccount> accs = q.getResultList();
		for (ContractAccount ca : accs) {
			try {
				Contract c = new Contract();
				c.init(ca.getAccountId(), ca.getSourceCode());
				c.setProvider(this);
				synchronized (contracts) {
					contracts.put(ca.getAccountId(), c);
				}
			} catch (Exception e) {
				throw new QwicsChainException(e);
			}
		}
	}

	public void putContractAccount(CreateContractTransaction ta) throws Exception {
		ContractAccount rec = em.find(ContractAccount.class, ta.getReceiverAccountId());
		if (rec == null) {
			// Create new contract account
			rec = new ContractAccount();
			rec.setAccountId(ta.getReceiverAccountId());
			rec.setCoins(ta.getCoins());
			rec.setPubKey(ta.getData());
			rec.setSourceCode(ta.getSourceCode());
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
			// No updates possible
			throw new QwicsChainException("Contract manipulation detected!");
		}
		try {
			Contract c = new Contract();
			c.init(rec.getAccountId(), rec.getSourceCode());
			c.setProvider(this);
			synchronized (contracts) {
				contracts.put(rec.getAccountId(), c);
			}
		} catch (Exception e) {
			throw new QwicsChainException(e);
		}
	}

	@Schedule(hour = "*", minute = "0", second = "0", persistent = false)
	public void onHour() {
		List<Long> ids = null;
		synchronized (contracts) {
			ids = new ArrayList<Long>(contracts.keySet());
		}
		for (int i = 0; i < ids.size(); i++) {
			Contract co = null;
			synchronized (contracts) {
				if ((co = contracts.get(ids.get(i))) != null) {
					try {
						co.onHour();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public void receive(Transaction ta) {
		try {
			if (ta instanceof CreateContractTransaction) {
				// Do not receive your own creation transaction
				return;
			}
			if (ta.getTransactionId().getAccountId() == ta.getReceiverAccountId()) {
				// Ignore your own transactions sent
				return;
			}
			Contract co = null;
			synchronized (contracts) {
				if ((co = contracts.get(ta.getReceiverAccountId())) != null) {
					co.receive(ta.getTransactionId().getAccountId(), ta.getAction(), ta.getCoins(), ta.getAsset(),
							ta.getData());
					return;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return;
	}

	@Override
	public void send(long senderId, long receiverId, int action, BigDecimal coins, Asset asset, byte[] data) {
		long tId = nodeData.getLastTransactionId(senderId) + 1;
		Transaction ta = new Transaction();
		ta.setTransactionId(new TransactionId(senderId, tId));
		ta.setPrevTransactionId(new TransactionId(senderId, tId - 1));
		ta.setCoins(coins);
		ta.setData(data);
		ta.setAction(action);
		ta.setReceiverAccountId(receiverId);
		ta.setValidated(false);
		ta.setAsset(asset);

		// Create only assets that are issued by yourself
		if (ta.getAction() == Transaction.ACTION_CREATEASSET) {
			Account a = em.find(Account.class, ta.getTransactionId().getAccountId());
			if (ta.getAsset().getIssuerAccountId() != ta.getTransactionId().getAccountId()) {
				return;
			}
			try {
				if (!ta.getAsset().verify(a.retrievePublicKey())) {
					return;
				}
			} catch (Exception e) {
				return;
			}
		}

		// Do not transfer an asset you do not have or to a non-existing account
		if (ta.getAction() == Transaction.ACTION_TRANSFERASSET) {
			Account a = em.find(Account.class, ta.getTransactionId().getAccountId());
			int i = a.indexOf(ta.getAsset());
			if (i < 0) {
				return;
			}
			if (a.getAssets().get(i).getAmount().compareTo(ta.getAsset().getAmount()) < 0) {
				return;
			}
			a = em.find(Account.class, ta.getReceiverAccountId());
			if (a == null) {
				return;
			}
		}

		if (ta.getAction() == Transaction.ACTION_TRANSFERCOINS) {
			Account a = em.find(Account.class, ta.getTransactionId().getAccountId());
			if (a.getCoins().compareTo(ta.getCoins().add(new BigDecimal(0.01))) < 0) {
				return;
			}
			a = em.find(Account.class, ta.getReceiverAccountId());
			if (a == null) {
				if (ta.getData() == null) {
					// Pubkey for new account not included
					return;
				} else {
					// Check if valid pubkey
					try {
						KeyFactory keyFactory = KeyFactory.getInstance("ECIES", "FlexiEC");
						keyFactory.generatePublic(new X509EncodedKeySpec(ta.getData()));

						long id = 0L | MurmurHash3.murmurhash3_x86_32(ta.getData(), 0, ta.getData().length, 33);
						if (ta.getReceiverAccountId() != id) {
							return;
						}
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}
				}
			}
		}

		nodeData.confirmTransaction(ta);
	}

	@Override
	public void save(long accountId, String identifier, Serializable object) {
		ContractStorageObject o = em.find(ContractStorageObject.class, identifier);
		if (o != null) {
			o.setObject(object);
			em.merge(o);
		} else {
			o = new ContractStorageObject();
			o.setObjectId(new ContractStorageObjectId(accountId, identifier));
			o.setObject(object);
			em.persist(o);
		}
	}

	@Override
	public Serializable load(long accountId, String identifier) {
		ContractStorageObject o = em.find(ContractStorageObject.class,
				new ContractStorageObjectId(accountId, identifier));
		if (o != null) {
			return o.getObject();
		}
		return null;
	}

	@Override
	public boolean verify(Asset asset) {
		try {
			Account issuer = em.find(Account.class, asset.getIssuerAccountId());
			return asset.verify(issuer.retrievePublicKey());
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public Account getAccount(long accountId) {
		try {
			return em.find(Account.class, accountId);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void log(String msg) {
	}	
}
