code = """
#include <Python.h>
#include "structmember.h"

typedef struct {
    PyObject_HEAD
    int number;
} ObjectWithMember;

PyObject* ObjectWithMember_new(PyTypeObject *type, PyObject *args, PyObject *kwds) {
    ObjectWithMember* self = (ObjectWithMember *)type->tp_alloc(type, 0);
    if (self != NULL) {
        self->number = 0;
    }
    return (PyObject *)self;
}

int ObjectWithMember_init(ObjectWithMember* self, PyObject* args, PyObject* kwds) {
    if (!PyArg_ParseTuple(args, "i", &self->number)) {
        return -1;
    }
    return 0;
}

static PyMemberDef ObjectWithMember_members[] = {
    {"number", T_INT, offsetof(ObjectWithMember, number), 0, ""},
    {NULL}
};

static PyTypeObject ObjectWithMemberType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "module.ObjectWithMember", /* tp_name */
    sizeof(ObjectWithMember),  /* tp_basicsize */
    0,                         /* tp_itemsize */
    0,                         /* tp_dealloc */
    0,                         /* tp_print */
    0,                         /* tp_getattr */
    0,                         /* tp_setattr */
    0,                         /* tp_reserved */
    0,                         /* tp_repr */
    0,                         /* tp_as_number */
    0,                         /* tp_as_sequence */
    0,                         /* tp_as_mapping */
    0,                         /* tp_hash  */
    0,                         /* tp_call */
    0,                         /* tp_str */
    0,                         /* tp_getattro */
    0,                         /* tp_setattro */
    0,                         /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT |
        Py_TPFLAGS_BASETYPE,   /* tp_flags */
    "",                        /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    0,                         /* tp_methods */
    ObjectWithMember_members,  /* tp_members */
    0,                         /* tp_getset */
    0,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    (initproc)ObjectWithMember_init,      /* tp_init */
    0,                         /* tp_alloc */
    ObjectWithMember_new,      /* tp_new */
};

static PyModuleDef module = {
    PyModuleDef_HEAD_INIT,
    "module",
    "",
    -1,
    NULL, NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC
PyInit_c_member_access_module(void) {
    if (PyType_Ready(&ObjectWithMemberType) < 0) {
        return NULL;
    }

    PyObject* m = PyModule_Create(&module);
    if (m == NULL) {
        return NULL;
    }

    Py_INCREF(&ObjectWithMemberType);
    PyModule_AddObject(m, "ObjectWithMember", (PyObject *)&ObjectWithMemberType);
    return m;
}
"""


ccompile("c_member_access_module", code)
import c_member_access_module


def do_stuff(foo):
    for i in range(50000):
        local_a = foo.number + 1
        foo.number = local_a % 5

    return foo.number


def measure(num):
    for i in range(num):
        result = do_stuff(c_member_access_module.ObjectWithMember(42))

    print(result)


def __benchmark__(num=50000):
    measure(num)
