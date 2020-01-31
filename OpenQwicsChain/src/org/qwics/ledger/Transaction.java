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

package org.qwics.ledger;

import java.io.Serializable;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MapKeyColumn;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import de.flexiprovider.common.util.ByteUtils;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Transaction implements Serializable {
	private static final long serialVersionUID = -7059537523880882942L;

	public static final int ACTION_CREATEASSET = 1;
	public static final int ACTION_TRANSFERASSET = 2;
	public static final int ACTION_TRANSFERCOINS = 3;

	@EmbeddedId
	@AttributeOverrides({ @AttributeOverride(name = "accountId", column = @Column(name = "account_id")),
			@AttributeOverride(name = "transId", column = @Column(name = "trans_id")) })
	private TransactionId transactionId;

	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "accountId", column = @Column(name = "prev_account_id")),
			@AttributeOverride(name = "transId", column = @Column(name = "prev_trans_id")) })
	private TransactionId prevTransactionId;

	private boolean validated = false;
	private boolean tainted = false;
	private boolean candidate = false;

	@ElementCollection(fetch = FetchType.EAGER)
	@MapKeyColumn(name = "hostname")
	@Column(name = "signature")
	private Map<String, byte[]> nodeSignatures = new HashMap<String, byte[]>();
	@Temporal(TemporalType.TIMESTAMP)
	private Date bookingDate = null;
	private byte[] prevHash;
	private byte[] hash;
	private byte[] signature;

	private int action = 0;
	private long receiverAccountId = 0;
	private byte[] data;
	private BigDecimal coins = new BigDecimal(0);
	private Asset asset;

	public Transaction() {
		super();
	}

	public TransactionId getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(TransactionId transactionId) {
		this.transactionId = transactionId;
	}

	public TransactionId getPrevTransactionId() {
		return prevTransactionId;
	}

	public void setPrevTransactionId(TransactionId prevTransactionId) {
		this.prevTransactionId = prevTransactionId;
	}

	public boolean isValidated() {
		return validated;
	}

	public void setValidated(boolean validated) {
		this.validated = validated;
	}

	public boolean isTainted() {
		return tainted;
	}

	public void setTainted(boolean tainted) {
		this.tainted = tainted;
	}

	public boolean isCandidate() {
		return candidate;
	}

	public void setCandidate(boolean candidate) {
		this.candidate = candidate;
	}

	public byte[] getPrevHash() {
		return prevHash;
	}

	public void setPrevHash(byte[] prevHash) {
		this.prevHash = prevHash;
	}

	public byte[] getHash() {
		return hash;
	}

	public void setHash(byte[] hash) {
		this.hash = hash;
	}

	public byte[] getSignature() {
		return signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public int getAction() {
		return action;
	}

	public void setAction(int action) {
		this.action = action;
	}

	public long getReceiverAccountId() {
		return receiverAccountId;
	}

	public void setReceiverAccountId(long receiverAccountId) {
		this.receiverAccountId = receiverAccountId;
	}

	public Map<String, byte[]> getNodeSignatures() {
		return nodeSignatures;
	}

	public void setNodeSignatures(Map<String, byte[]> nodeSignatures) {
		this.nodeSignatures = nodeSignatures;
	}

	public Date getBookingDate() {
		return bookingDate;
	}

	public void setBookingDate(Date bookingDate) {
		this.bookingDate = bookingDate;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public Asset getAsset() {
		return asset;
	}

	public void setAsset(Asset asset) {
		this.asset = asset;
	}

	public BigDecimal getCoins() {
		return coins;
	}

	public void setCoins(BigDecimal coins) {
		this.coins = coins;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + action;
		result = prime * result + ((asset == null) ? 0 : asset.hashCode());
		result = prime * result + ((bookingDate == null) ? 0 : bookingDate.hashCode());
		result = prime * result + ((coins == null) ? 0 : coins.hashCode());
		result = prime * result + Arrays.hashCode(data);
		result = prime * result + Arrays.hashCode(prevHash);
		result = prime * result + ((prevTransactionId == null) ? 0 : prevTransactionId.hashCode());
		result = prime * result + (int) (receiverAccountId ^ (receiverAccountId >>> 32));
		result = prime * result + ((transactionId == null) ? 0 : transactionId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Transaction other = (Transaction) obj;
		if (action != other.action)
			return false;
		if (asset == null) {
			if (other.asset != null)
				return false;
		} else if (!asset.equals(other.asset))
			return false;
		if (bookingDate == null) {
			if (other.bookingDate != null)
				return false;
		} else if (!bookingDate.equals(other.bookingDate))
			return false;
		if (coins == null) {
			if (other.coins != null)
				return false;
		} else if (coins.compareTo(other.coins) != 0)
			return false;
		if (!Arrays.equals(data, other.data))
			return false;
		if (!Arrays.equals(prevHash, other.prevHash))
			return false;
		if (prevTransactionId == null) {
			if (other.prevTransactionId != null)
				return false;
		} else if (!prevTransactionId.equals(other.prevTransactionId))
			return false;
		if (receiverAccountId != other.receiverAccountId)
			return false;
		if (transactionId == null) {
			if (other.transactionId != null)
				return false;
		} else if (!transactionId.equals(other.transactionId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer().append(transactionId.toString()).append(";")
				.append(prevTransactionId.toString()).append(";").append(action).append(";").append(receiverAccountId);
		if (asset != null) {
			b.append(";").append(asset.toString());
		}
		b.append(";").append(coins.setScale(2).toString());
		if (data != null) {
			b.append(";").append(ByteUtils.toHexString(data));
		} else { 
			b.append(";");
		}
		if (prevHash != null) {
			b.append(";").append(ByteUtils.toHexString(prevHash));
		}
/*
		if (bookingDate != null) {
			b.append(";").append(bookingDate.toString());
		}
*/
		return b.toString();
	}

	public byte[] getSecureHash() throws Exception {
		MessageDigest md = MessageDigest.getInstance("RIPEMD320", "FlexiCore");
		md.update(this.toString().getBytes());
		byte[] h = md.digest();
		return h;
	}

	public void sign(PrivateKey privKey) throws Exception {
		Signature sig = Signature.getInstance("SHA512withECDSA", "FlexiEC");
		sig.initSign(privKey);
		sig.update(this.toString().getBytes());
		signature = sig.sign();
	}

	public boolean verify(PublicKey pubKey) throws Exception {
		Signature sig = Signature.getInstance("SHA512withECDSA", "FlexiEC");
		sig.initVerify(pubKey);
		sig.update(this.toString().getBytes());
		return sig.verify(signature);
	}

	public byte[] getSignature(PrivateKey privKey) throws Exception {
		Signature sig = Signature.getInstance("SHA512withECDSA", "FlexiEC");
		sig.initSign(privKey);
		sig.update(this.toString().getBytes());
		return sig.sign();
	}

	public boolean verifySignature(byte[] sign, PublicKey pubKey) throws Exception {
		Signature sig = Signature.getInstance("SHA512withECDSA", "FlexiEC");
		sig.initVerify(pubKey);
		sig.update(this.toString().getBytes());
		return sig.verify(sign);
	}

}
