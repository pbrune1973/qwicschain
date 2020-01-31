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
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Account implements Serializable {
	private static final long serialVersionUID = -1491830131288045031L;

	@Id
	@Column(name = "account_id")
	private long accountId;
	private BigDecimal coins;
	private byte[] pubKey;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "account_assets", joinColumns = @JoinColumn(name = "owner_account_id"))
	private List<Asset> assets;

	public Account() {
		super();
		accountId = 0;
		coins = new BigDecimal(0.0);
		pubKey = null;
		assets = new ArrayList<Asset>();
	}

	public long getAccountId() {
		return accountId;
	}

	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}

	public BigDecimal getCoins() {
		return coins;
	}

	public void setCoins(BigDecimal coins) {
		this.coins = coins;
	}

	public byte[] getPubKey() {
		return pubKey;
	}

	public void setPubKey(byte[] pubKey) {
		this.pubKey = pubKey;
	}

	public List<Asset> getAssets() {
		return assets;
	}

	public void setAssets(List<Asset> assets) {
		this.assets = assets;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (accountId ^ (accountId >>> 32));
		result = prime * result + ((assets == null) ? 0 : assets.hashCode());
		result = prime * result + ((coins == null) ? 0 : coins.hashCode());
		result = prime * result + Arrays.hashCode(pubKey);
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
		Account other = (Account) obj;
		if (accountId != other.accountId)
			return false;
		if (assets == null) {
			if (other.assets != null)
				return false;
		} else if (!assets.equals(other.assets))
			return false;
		if (coins == null) {
			if (other.coins != null)
				return false;
		} else if (coins.compareTo(other.coins) != 0)
			return false;
		if (!Arrays.equals(pubKey, other.pubKey))
			return false;
		return true;
	}

	public PublicKey retrievePublicKey() throws Exception {
		KeyFactory keyFactory = KeyFactory.getInstance("ECIES", "FlexiEC");
		return keyFactory.generatePublic(new X509EncodedKeySpec(this.pubKey));
	}

	public int indexOf(Asset a) {
		for (int i = 0; i < assets.size(); i++) {
			if (assets.get(i).equals(a)) {
				return i;
			}
		}
		return -1;
	}
}
