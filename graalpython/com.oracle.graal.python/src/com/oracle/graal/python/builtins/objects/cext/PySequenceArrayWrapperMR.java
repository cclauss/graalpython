/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.GetTypeIDNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ReadArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ToNativeStorageNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.WriteArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.StorageToNativeNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@MessageResolution(receiverType = PySequenceArrayWrapper.class)
public class PySequenceArrayWrapperMR {

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child GetTypeIDNode getTypeIDNode = GetTypeIDNode.create();

        public Object access(PySequenceArrayWrapper object) {
            return getTypeIDNode.execute(object.getDelegate());
        }

    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ReadArrayItemNode readArrayItemNode;

        public Object access(PySequenceArrayWrapper object, Object key) {
            return getReadArrayItemNode().execute(object.getDelegate(), key);
        }

        private ReadArrayItemNode getReadArrayItemNode() {
            if (readArrayItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArrayItemNode = insert(ReadArrayItemNode.create());
            }
            return readArrayItemNode;
        }

    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private WriteArrayItemNode writeArrayItemNode;

        public Object access(PySequenceArrayWrapper object, Object key, Object value) {
            if (writeArrayItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeArrayItemNode = insert(WriteArrayItemNode.create());
            }
            writeArrayItemNode.execute(object.getDelegate(), key, value);

            // A C expression assigning to an array returns the assigned value.
            return value;
        }

    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class ReadArrayItemNode extends Node {

        @Child private ToSulongNode toSulongNode;

        public abstract Object execute(Object arrayObject, Object idx);

        @Specialization
        Object doTuple(PTuple tuple, long idx,
                        @Cached("createTupleGetItem()") TupleBuiltins.GetItemNode getItemNode) {
            return getToSulongNode().execute(getItemNode.execute(tuple, idx));
        }

        @Specialization
        Object doTuple(PList list, long idx,
                        @Cached("createListGetItem()") ListBuiltins.GetItemNode getItemNode) {
            return getToSulongNode().execute(getItemNode.execute(list, idx));
        }

        /**
         * The sequence array wrapper of a {@code bytes} object represents {@code ob_sval}. We type
         * it as {@code uint8_t*} and therefore we get a byte index. However, we return
         * {@code uint64_t} since we do not know how many bytes are requested.
         */
        @Specialization
        long doBytesI32(PBytes bytes, long byteIdx) {
            int len = bytes.len();
            // simulate sentinel value
            if (byteIdx == len) {
                return 0L;
            }
            int i = (int) byteIdx;
            byte[] barr = bytes.getInternalByteArray();
            long result = 0;
            result |= barr[i];
            if (i + 1 < len)
                result |= ((long) barr[i + 1] << 8L) & 0xFF00L;
            if (i + 2 < len)
                result |= ((long) barr[i + 2] << 16L) & 0xFF0000L;
            if (i + 3 < len)
                result |= ((long) barr[i + 3] << 24L) & 0xFF000000L;
            if (i + 4 < len)
                result |= ((long) barr[i + 4] << 32L) & 0xFF00000000L;
            if (i + 5 < len)
                result |= ((long) barr[i + 5] << 40L) & 0xFF0000000000L;
            if (i + 6 < len)
                result |= ((long) barr[i + 6] << 48L) & 0xFF000000000000L;
            if (i + 7 < len)
                result |= ((long) barr[i + 7] << 56L) & 0xFF00000000000000L;
            return result;
        }

        @Specialization(guards = {"!isTuple(object)", "!isList(object)"})
        Object doGeneric(Object object, long idx,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode) {
            return getToSulongNode().execute(getItemNode.executeObject(object, idx));
        }

        protected static ListBuiltins.GetItemNode createListGetItem() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected static TupleBuiltins.GetItemNode createTupleGetItem() {
            return TupleBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected boolean isTuple(Object object) {
            return object instanceof PTuple;
        }

        protected boolean isList(Object object) {
            return object instanceof PList;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        public static ReadArrayItemNode create() {
            return ReadArrayItemNodeGen.create();
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class WriteArrayItemNode extends Node {
        @Child private CExtNodes.ToJavaNode toJavaNode;

        public abstract Object execute(Object arrayObject, Object idx, Object value);

        @Specialization
        Object doTuple(PTuple tuple, long idx, Object value) {
            Object[] store = tuple.getArray();
            // TODO(fa) do proper index conversion
            store[(int) idx] = getToJavaNode().execute(value);
            return value;
        }

        @Specialization
        Object doList(PList list, long idx, Object value,
                        @Cached("createListSetItem()") ListBuiltins.SetItemNode setItemNode) {
            return setItemNode.execute(list, idx, getToJavaNode().execute(value));
        }

        @Specialization
        Object doBytes(PBytes tuple, long idx, byte value) {
            // TODO(fa) do proper index conversion
            tuple.getInternalByteArray()[(int) idx] = value;
            return value;
        }

        @Specialization
        Object doByteArray(PByteArray tuple, long idx, byte value) {
            // TODO(fa) do proper index conversion
            tuple.getInternalByteArray()[(int) idx] = value;
            return value;
        }

        @Specialization
        Object doGeneric(Object tuple, Object idx, Object value,
                        @Cached("createSetItem()") LookupAndCallTernaryNode setItemNode) {
            setItemNode.execute(tuple, idx, value);
            return value;
        }

        protected static ListBuiltins.SetItemNode createListSetItem() {
            return ListBuiltinsFactory.SetItemNodeFactory.create();
        }

        protected static LookupAndCallTernaryNode createSetItem() {
            return LookupAndCallTernaryNode.create(__SETITEM__);
        }

        private CExtNodes.ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(CExtNodes.ToJavaNode.create());
            }
            return toJavaNode;
        }

        public static WriteArrayItemNode create() {
            return WriteArrayItemNodeGen.create();
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToNativeArrayNode toPyObjectNode = ToNativeArrayNode.create();

        Object access(PySequenceArrayWrapper obj) {
            if (!obj.isNative()) {
                obj.setNativePointer(toPyObjectNode.execute(obj));
            }
            return obj;
        }
    }

    static class ToNativeArrayNode extends TransformToNativeNode {
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaObject;
        @Child private PCallNativeNode callNativeBinary;
        @Child private ToNativeStorageNode toNativeStorageNode;

        public Object execute(PySequenceArrayWrapper object) {
            // TODO correct element size
            Object delegate = object.getDelegate();
            if (delegate instanceof PSequence) {
                NativeSequenceStorage nativeStorage = getToNativeStorageNode().execute(((PSequence) delegate).getSequenceStorage());
                if (nativeStorage == null) {
                    throw new AssertionError("could not allocate native storage");
                }
                return nativeStorage;

            }
            return null;
        }

        protected boolean isNonNative(PythonClass klass) {
            return !(klass instanceof PythonNativeClass);
        }

        private ToNativeStorageNode getToNativeStorageNode() {
            if (toNativeStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toNativeStorageNode = insert(ToNativeStorageNode.create());
            }
            return toNativeStorageNode;
        }

        public static ToNativeArrayNode create() {
            return new ToNativeArrayNode();
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        Object access(PySequenceArrayWrapper obj) {
            return obj.isNative();
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private Node asPointerNode;

        long access(PySequenceArrayWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = obj.getNativePointer();
            if (nativePointer instanceof TruffleObject) {
                if (asPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asPointerNode = insert(Message.AS_POINTER.createNode());
                }
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                } catch (UnsupportedMessageException e) {
                    throw e.raise();
                }
            }
            return (long) nativePointer;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    abstract static class GetTypeIDNode extends PBaseNode {

        @Child private PCallNativeNode callUnaryNode = PCallNativeNode.create();

        @CompilationFinal TruffleObject funGetByteArrayTypeID;
        @CompilationFinal TruffleObject funGetPtrArrayTypeID;

        public abstract Object execute(Object delegate);

        private Object callGetByteArrayTypeID(long len) {
            if (funGetByteArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetByteArrayTypeID = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID);
            }
            return callUnaryNode.execute(funGetByteArrayTypeID, new Object[]{len});
        }

        private Object callGetPtrArrayTypeID(long len) {
            if (funGetPtrArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetPtrArrayTypeID = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_GET_PTR_ARRAY_TYPE_ID);
            }
            return callUnaryNode.execute(funGetPtrArrayTypeID, new Object[]{len});
        }

        @Specialization
        Object doTuple(PTuple tuple) {
            return callGetPtrArrayTypeID(tuple.len());
        }

        @Specialization
        Object doList(PList list) {
            return callGetPtrArrayTypeID(list.len());
        }

        @Specialization
        Object doBytes(PBytes bytes) {
            return callGetByteArrayTypeID(bytes.len());
        }

        @Specialization
        Object doByteArray(PByteArray bytes) {
            return callGetByteArrayTypeID(bytes.len());
        }

        @Specialization(guards = {"!isTuple(object)", "!isList(object)"})
        Object doGeneric(Object object,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode getLenNode) {
            try {
                return callGetPtrArrayTypeID(getLenNode.executeInt(object));
            } catch (UnexpectedResultException e) {
                // TODO do something useful
                throw new AssertionError();
            }
        }

        protected static ListBuiltins.GetItemNode createListGetItem() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected static TupleBuiltins.GetItemNode createTupleGetItem() {
            return TupleBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected boolean isTuple(Object object) {
            return object instanceof PTuple;
        }

        protected boolean isList(Object object) {
            return object instanceof PList;
        }

        public static GetTypeIDNode create() {
            return GetTypeIDNodeGen.create();
        }
    }

    static abstract class ToNativeStorageNode extends TransformToNativeNode {
        @Child private StorageToNativeNode storageToNativeNode;

        public abstract NativeSequenceStorage execute(SequenceStorage object);

        @Specialization
        NativeSequenceStorage doByteStorage(ByteSequenceStorage s) {
            return getStorageToNativeNode().execute(s.getInternalByteArray());
        }

        @Specialization
        NativeSequenceStorage doIntStorage(IntSequenceStorage s) {
            return getStorageToNativeNode().execute(s.getInternalIntArray());
        }

        @Specialization
        NativeSequenceStorage doLongStorage(LongSequenceStorage s) {
            return getStorageToNativeNode().execute(s.getInternalLongArray());
        }

        @Specialization
        NativeSequenceStorage doDoubleStorage(DoubleSequenceStorage s) {
            return getStorageToNativeNode().execute(s.getInternalDoubleArray());
        }

        @Specialization
        NativeSequenceStorage doObjectStorage(ObjectSequenceStorage s) {
            return getStorageToNativeNode().execute(s.getInternalArray());
        }

        @Specialization
        NativeSequenceStorage doNativeStorage(NativeSequenceStorage s) {
            return s;
        }

        @Specialization
        NativeSequenceStorage doEmptyStorage(@SuppressWarnings("unused") EmptySequenceStorage s) {
            // TODO(fa): not sure if that completely reflects semantics
            return getStorageToNativeNode().execute(new byte[0]);
        }

        @Fallback
        NativeSequenceStorage doGeneric(@SuppressWarnings("unused") SequenceStorage s) {
            return null;
        }

        private StorageToNativeNode getStorageToNativeNode() {
            if (storageToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storageToNativeNode = insert(StorageToNativeNode.create());
            }
            return storageToNativeNode;
        }

        public static ToNativeStorageNode create() {
            return ToNativeStorageNodeGen.create();
        }
    }

}
