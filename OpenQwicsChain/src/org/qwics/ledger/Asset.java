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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class Asset implements Serializable {
	private static final long serialVersionUID = -8487967965674080057L;

	public static final int EMPTY = 0;
	public static final int NOTE_OF_DEBT = 1;

	private int type = NOTE_OF_DEBT;
	private String unit;
	private BigDecimal quantity;

	@Column(name = "issuer_account_id")
	private long issuerAccountId = 0;
	@Column(name = "issuer_signature")
	private byte[] issuerSignature;

	private BigDecimal amount = new BigDecimal(1.0);

	public Asset(int type, String unit, BigDecimal quantity, long issuerAccountId, PrivateKey privKey)
			throws Exception {
		super();
		this.type = type;
		this.unit = unit;
		this.quantity = quantity;
		this.issuerAccountId = issuerAccountId;

		if (type != EMPTY) {
			Signature sig = Signature.getInstance("SHA512withECDSA", "FlexiEC");
			sig.initSign(privKey);
			sig.update(this.toString().getBytes());
			issuerSignature = sig.sign();
		}
	}

	public Asset() {
		super();
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer().append(type).append(";").append(unit).append(";").append(quantity.setScale(2).toString())
				.append(";").append(issuerAccountId);
		return b.toString();
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public long getIssuerAccountId() {
		return issuerAccountId;
	}

	public void setIssuerAccountId(long issuerAccountId) {
		this.issuerAccountId = issuerAccountId;
	}

	public byte[] getIssuerSignature() {
		return issuerSignature;
	}

	public void setIssuerSignature(byte[] issuerSignature) {
		this.issuerSignature = issuerSignature;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (issuerAccountId ^ (issuerAccountId >>> 32));
		result = prime * result + ((quantity == null) ? 0 : quantity.hashCode());
		result = prime * result + type;
		result = prime * result + ((unit == null) ? 0 : unit.hashCode());
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
		Asset other = (Asset) obj;
		if (issuerAccountId != other.issuerAccountId)
			return false;
		if (quantity == null) {
			if (other.quantity != null)
				return false;
		} else if (quantity.compareTo(other.quantity) != 0)
			return false;
		if (type != other.type)
			return false;
		if (unit == null) {
			if (other.unit != null)
				return false;
		} else if (!unit.equals(other.unit))
			return false;
		return true;
	}

	public boolean verify(PublicKey pubKey) throws Exception {
		Signature sig = Signature.getInstance("SHA512withECDSA", "FlexiEC");
		sig.initVerify(pubKey);
		sig.update(this.toString().getBytes());
		return sig.verify(issuerSignature);
	}
	
}
