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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeMap;

public class TaList implements List<TaData>, Serializable {
	private static final long serialVersionUID = -1489524176260065412L;

	private TreeMap<Double,List<TaData>> taMap = new TreeMap <Double,List<TaData>>();
	private ArrayList<TaData> taArray = new ArrayList<TaData>();
	
	private double getPrice(TaData ta) {
		BigDecimal res = ta.getCoins();
		if (ta.getAsset().getAmount().compareTo(new BigDecimal(0.0)) > 0) {
			// Obtain single price
			res = res.divide(ta.getAsset().getAmount(),2,BigDecimal.ROUND_HALF_UP);
		}
		return res.doubleValue();
	}
	
	private void put(TaData ta) {
		Double p = getPrice(ta);
		List<TaData> l = taMap.get(p);
		if (l == null) {
			l = new ArrayList<TaData>();
			l.add(ta);
			taMap.put(p,l);
		} else {
			l.add(ta);			
		}
	}

	private void removeFromMap(TaData ta) {
		Set<Double> prices = taMap.keySet();
		for (Double p : prices) {
			List<TaData> l = taMap.get(p);
			if (l != null) {
				for (int i = 0; i < l.size(); i++) {
					if (l.get(i) == ta) {
						l.remove(i);
					}
				}
				if (l.size() == 0) {
					l = null;
				}
			}
			if (l == null) {
				taMap.put(p, null);
			}
		}
	}
	
	public BigDecimal getAmount(Double p) {
		BigDecimal sum = new BigDecimal(0.0);
		List<TaData> l = taMap.get(p);
		if (l != null) {
			for (TaData t : l) {
				sum = sum.add(t.getAsset().getAmount());
			}
		}
		return sum;
	}	

	public List<Double> getPrices() {
		return new ArrayList<Double>(taMap.keySet());
	}

	@Override
	public int size() {
		return taArray.size();
	}

	@Override
	public boolean isEmpty() {
		return taArray.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return taArray.contains(o);
	}

	@Override
	public Iterator<TaData> iterator() {
		return taArray.iterator();
	}

	@Override
	public Object[] toArray() {
		return taArray.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return taArray.toArray(a);
	}

	@Override
	public boolean add(TaData e) {
		put(e);
		return taArray.add(e);
	}

	@Override
	public boolean remove(Object o) {
		removeFromMap((TaData)o);
		return taArray.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return taArray.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends TaData> c) {
		for (TaData ta : c) {
			put(ta);
		}
		return taArray.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection<? extends TaData> c) {
		for (TaData ta : c) {
			put(ta);
		}
		return taArray.addAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		for (TaData ta : (Collection<TaData>)c) {
			removeFromMap(ta);
		}
		return taArray.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return taArray.retainAll(c);
	}

	@Override
	public void clear() {
		taArray.clear();
		taMap.clear();
	}

	@Override
	public TaData get(int index) {
		return taArray.get(index);
	}

	@Override
	public TaData set(int index, TaData element) {
		return taArray.set(index, element);	}

	@Override
	public void add(int index, TaData element) {
		put(element);
		taArray.add(index,element);
	}

	@Override
	public TaData remove(int index) {
		removeFromMap(taArray.get(index));
		return taArray.remove(index);
	}

	@Override
	public int indexOf(Object o) {
		return taArray.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return taArray.lastIndexOf(o);
	}

	@Override
	public ListIterator<TaData> listIterator() {
		return taArray.listIterator();
	}

	@Override
	public ListIterator<TaData> listIterator(int index) {
		return taArray.listIterator(index);
	}

	@Override
	public List<TaData> subList(int fromIndex, int toIndex) {
		return taArray.subList(fromIndex, toIndex);
	}
	
}
