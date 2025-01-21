/*******************************************************************************
 * Copyright (c) 2009, 2024 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Map;

public abstract class ExposedAbstractMatcher {


	public final Map<String, VarInsnNode> vars = new HashMap<String, VarInsnNode>();


	private AbstractMatcher delegate = new AbstractMatcher() {
	};
	public void setCursor(AbstractInsnNode cursor) {
		delegate.cursor = cursor;
	}
	public AbstractInsnNode cursor() {
		return delegate.cursor;
	}

	/**
	 * Sets {@link #cursor} to first instruction of method if it is
	 * <code>ALOAD 0</code>, otherwise sets it to <code>null</code>.
	 */
	public final void firstIsALoad0(final MethodNode methodNode) {
		delegate.firstIsALoad0(methodNode);
	}

	/**
	 * Moves {@link #cursor} to next instruction if it is {@link TypeInsnNode}
	 * with given opcode and operand, otherwise sets it to <code>null</code>.
	 */
	public final void nextIsType(final int opcode, final String desc) {
		delegate.nextIsType(opcode, desc);
	}

	/**
	 * Moves {@link #cursor} to next instruction if it is {@link MethodInsnNode}
	 * with given opcode, owner, name and descriptor, otherwise sets it to
	 * <code>null</code>.
	 */
	public final void nextIsInvoke(final int opcode, final String owner,
                                   final String name, final String descriptor) {
		delegate.nextIsInvoke(opcode, owner, name, descriptor);
	}

	/**
	 * Moves {@link #cursor} to next instruction if it is {@link FieldInsnNode}
	 * with given opcode, owner, name and descriptor, otherwise sets it to
	 * <code>null</code>.
	 */
	public final void nextIsField(final int opcode, final String owner,
                                  final String name, final String descriptor) {
		delegate.nextIsField(opcode, owner, name, descriptor);
	}

	public final void nextIsVar(final int opcode, final String name) {
		delegate.nextIsVar(opcode, name);
	}

	/**
	 * Moves {@link #cursor} to next instruction if it is
	 * <code>TABLESWITCH</code> or <code>LOOKUPSWITCH</code>, otherwise sets it
	 * to <code>null</code>.
	 */
	public final void nextIsSwitch() {
		delegate.nextIsSwitch();
	}

	/**
	 * Moves {@link #cursor} to next instruction if it has given opcode,
	 * otherwise sets it to <code>null</code>.
	 */
	public final void nextIs(final int opcode) {
		delegate.nextIs(opcode);
	}

	/**
	 * Moves {@link #cursor} to next instruction.
	 */
	public final void next() {
		delegate.next();
	}

	/**
	 * Moves {@link #cursor} through {@link AbstractInsnNode#FRAME},
	 * {@link AbstractInsnNode#LABEL}, {@link AbstractInsnNode#LINE}.
	 */
	public final void skipNonOpcodes() {
		delegate.skipNonOpcodes();
	}

	/**
	 * Returns first instruction from given and following it that is not
	 * {@link AbstractInsnNode#FRAME}, {@link AbstractInsnNode#LABEL},
	 * {@link AbstractInsnNode#LINE}.
	 */
	static AbstractInsnNode skipNonOpcodes(AbstractInsnNode cursor) {
		return AbstractMatcher.skipNonOpcodes(cursor);
	}

}
