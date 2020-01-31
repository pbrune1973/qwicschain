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
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.PBEParameterSpec;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

@Entity
public class NodeConfig implements Serializable {
	private static final long serialVersionUID = -8309822232574844389L;

	@Id
	private long id = 1;
	private byte[] encPrivKey;
	private byte[] pubKey;

	@OneToMany(fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE })
	private List<Node> knownNodes;

	public NodeConfig() {
		knownNodes = new ArrayList<Node>();
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public byte[] getEncPrivKey() {
		return encPrivKey;
	}

	public void setEncPrivKey(byte[] encPrivKey) {
		this.encPrivKey = encPrivKey;
	}

	public byte[] getPubKey() {
		return pubKey;
	}

	public void setPubKey(byte[] pubKey) {
		this.pubKey = pubKey;
	}

	public List<Node> getKnownNodes() {
		return knownNodes;
	}

	public void setKnownNodes(List<Node> knownNodes) {
		this.knownNodes = knownNodes;
	}

	public void setPrivateKey(PrivateKey privKey, SecretKey key) throws Exception {
		byte[] salt = new byte[]{ 0x12, 0x34, 056, (byte)0xAA, (byte)0xFF, 0x4F, (byte)0xAC, 0x10 };

		Cipher cipher = Cipher.getInstance("PBEWithMD5AndTripleDES");
		AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, 10);
		cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
		encPrivKey = cipher.doFinal(privKey.getEncoded());
	}

	public PrivateKey getPrivateKey(SecretKey key) throws Exception {
		byte[] privKey;
		byte[] salt = new byte[]{ 0x12, 0x34, 056, (byte)0xAA, (byte)0xFF, 0x4F, (byte)0xAC, 0x10 };

		Cipher cipher = Cipher.getInstance("PBEWithMD5AndTripleDES");
		AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, 10);
		cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
		privKey = cipher.doFinal(encPrivKey);
		
		KeyFactory keyFactory = KeyFactory.getInstance("ECIES", "FlexiEC");
		return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privKey));
	}

	public PublicKey getPublicKey() throws Exception {
		KeyFactory keyFactory = KeyFactory.getInstance("ECIES", "FlexiEC");
		return keyFactory.generatePublic(new X509EncodedKeySpec(this.pubKey));
	}
}
