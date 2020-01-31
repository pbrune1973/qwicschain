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

package org.qwics.ledger.connector;

import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.qwics.ledger.Transaction;
import org.qwics.ledger.ws.QwicsChainException;
import org.qwics.ledger.ws.QwicsChainNodeData;

@Singleton
@LocalBean
public class QwicsChainConnectorManager {
	@PersistenceContext(unitName = "QwicsDB")
	private EntityManager em;
	private HashMap<Long, ApplicationConnection> cons = new HashMap<Long, ApplicationConnection>();
	@EJB
	QwicsChainNodeData nodeData;

	public QwicsChainConnectorManager() {
	}

	@PostConstruct
	public void init() {
		Query q = em.createNativeQuery("SELECT ac FROM ApplicationConnection ac", ApplicationConnection.class);
		List<ApplicationConnection> c = q.getResultList();
		for (ApplicationConnection ac : c) {
			try {
				cons.put(ac.getAccountId(), ac);
			} catch (Exception e) {
				throw new QwicsChainException(e);
			}
		}
	}

	public boolean receive(Transaction ta) {
		try {
			ApplicationConnection ac = null;
			if ((ac = cons.get(ta.getReceiverAccountId())) != null) {
				InitialContext ctx = new InitialContext();
				TransactionHandler handler = (TransactionHandler) ctx
						.lookup(ac.getUrl() + "!org.qwics.ledger.connector.TransactionHandler");
				handler.processTransaction(ta.getTransactionId().getAccountId(), ta.getReceiverAccountId(),
						ta.getAction(), ta.getCoins(), ta.getAsset(), ta.getData());
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

}
