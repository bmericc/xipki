/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.commons.security.api.p11.remote;

import java.io.IOException;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.xipki.commons.security.api.BadASN1ObjectException;

/**
 *
 * <pre>
 * PSOTemplate ::= SEQUENCE {
 *   slotAndKeyIdentifier   SlotAndKeyIdentifer
 *   message        OCTET STRING,
 *   }
 * </pre>
 *
 * @author Lijun Liao
 * @since 2.0
 */

public class PSOTemplate extends ASN1Object {

  private SlotAndKeyIdentifer slotAndKeyIdentifier;

  private byte[] message;

  private PSOTemplate(
      final ASN1Sequence seq)
  throws BadASN1ObjectException {
    final int n = seq.size();
    if (n != 2) {
      StringBuilder sb = new StringBuilder(100);
      sb.append("wrong number of elements in sequence 'PSOTemplate'");
      sb.append(", is '").append(n).append("'");
      sb.append(", but expected '").append(2).append("'");
      throw new BadASN1ObjectException(sb.toString());
    }

    this.slotAndKeyIdentifier = SlotAndKeyIdentifer.getInstance(seq.getObjectAt(0));
    DEROctetString octetString = (DEROctetString) DEROctetString.getInstance(
        seq.getObjectAt(1));
    this.message = octetString.getOctets();
  }

  public PSOTemplate(
      final SlotAndKeyIdentifer slotAndKeyIdentifier,
      final byte[] message) {
    if (slotAndKeyIdentifier == null) {
      throw new IllegalArgumentException("slotAndKeyIdentifier could not be null");
    }
    if (message == null) {
      throw new IllegalArgumentException("message could not be null");
    }

    this.slotAndKeyIdentifier = slotAndKeyIdentifier;
    this.message = message;
  }

  public static PSOTemplate getInstance(
      final Object obj)
  throws BadASN1ObjectException {
    if (obj == null || obj instanceof PSOTemplate) {
      return (PSOTemplate) obj;
    }

    try {
      if (obj instanceof ASN1Sequence) {
        return new PSOTemplate((ASN1Sequence) obj);
      }

      if (obj instanceof byte[]) {
        return getInstance(ASN1Primitive.fromByteArray((byte[]) obj));
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new BadASN1ObjectException("unable to parse encoded PSOTemplate");
    }

    throw new BadASN1ObjectException("unknown object in PSOTemplate.getInstance(): "
        + obj.getClass().getName());
  }

  @Override
  public ASN1Primitive toASN1Primitive() {
    ASN1EncodableVector vector = new ASN1EncodableVector();
    vector.add(slotAndKeyIdentifier.toASN1Primitive());
    vector.add(new DEROctetString(message));
    return new DERSequence(vector);
  }

  public byte[] getMessage() {
    return message;
  }

  public SlotAndKeyIdentifer getSlotAndKeyIdentifer() {
    return slotAndKeyIdentifier;
  }

}
