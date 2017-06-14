// Implements a generic, thread-safe ordered map using a red-black tree.
package pgoutil

import (
	"fmt"
	rbt "github.com/emirpasic/gods/trees/redblacktree"
	"sync"
	"reflect"
)

// Generic comparator function that satisfies utils.Comparator
func comp(a, b interface{}) int {
	if reflect.TypeOf(a) != reflect.TypeOf(b) {
		panic(fmt.Sprintf("Arguments to comparator are not the same type: %T %T", a, b))
	}
	// hopefully no typos
	switch a := a.(type) {
	case bool:
		if !a && b.(bool) {
			return -1
		} else if !b.(bool) && a {
			return 1
		}
		return 0
	case int:
		if a < b.(int) {
			return -1
		} else if a > b.(int) {
			return 1
		}
		return 0
	case uint:
		if a < b.(uint) {
			return -1
		} else if a > b.(uint) {
			return 1
		}
		return 0
	case uint8:
		if a < b.(uint8) {
			return -1
		} else if a > b.(uint8) {
			return 1
		}
		return 0
	case uint16:
		if a < b.(uint16) {
			return -1
		} else if a > b.(uint16) {
			return 1
		}
		return 0
	case uint32:
		if a < b.(uint32) {
			return -1
		} else if a > b.(uint32) {
			return 1
		}
		return 0
	case uint64:
		if a < b.(uint64) {
			return -1
		} else if a > b.(uint64) {
			return 1
		}
		return 0
	case int8:
		if a < b.(int8) {
			return -1
		} else if a > b.(int8) {
			return 1
		}
		return 0
	case int16:
		if a < b.(int16) {
			return -1
		} else if a > b.(int16) {
			return 1
		}
		return 0
	case int32:
		if a < b.(int32) {
			return -1
		} else if a > b.(int32) {
			return 1
		}
		return 0
	case int64:
		if a < b.(int64) {
			return -1
		} else if a > b.(int64) {
			return 1
		}
		return 0
	case float32:
		if a < b.(float32) {
			return -1
		} else if a > b.(float32) {
			return 1
		}
		return 0
	case float64:
		if a < b.(float64) {
			return -1
		} else if a > b.(float64) {
			return 1
		}
		return 0
	case string:
		if a < b.(string) {
			return -1
		} else if a > b.(string) {
			return 1
		}
		return 0
	//compare by real then by imag
	case complex64:
		if real(a) < real(b.(complex64)) {
			return -1
		} else if real(a) > real(b.(complex64)) {
			return 1
		}
		if imag(a) < imag(b.(complex64)) {
			return -1
		} else if imag(a) > imag(b.(complex64)) {
			return 1
		}
		return 0
	case complex128:
		if real(a) < real(b.(complex128)) {
			return -1
		} else if real(a) > real(b.(complex128)) {
			return 1
		}
		if imag(a) < imag(b.(complex128)) {
			return -1
		} else if imag(a) > imag(b.(complex128)) {
			return 1
		}
		return 0
	case Tuple:
		b := b.(Tuple)
		for i := 0; i < a.Size(); i++ {
			if i == b.Size() {
				return 1
			}
			switch comp(a.At(i), b.At(i)) {
			case -1:
				return -1
			case 1:
				return 1
			}
		}
		if (a.Size() == b.Size()) {
			return 0
		}
		return -1
	// TODO add cases for sets and maps
	default:
		// check if this is a struct, ptr or slice
		// structs are compared based on each (exported) field (in order)
		// ptrs are compared based on pointed-to value
		// slices are compared in the same way as tuples
		v := reflect.ValueOf(a)
		w := reflect.ValueOf(b)
		switch v.Kind() {
		case reflect.Ptr:
			for v.Kind() == reflect.Ptr || v.Kind() == reflect.Interface {
				v = v.Elem()
				w = w.Elem()
			}
			return comp(v.Interface(), w.Interface())
		case reflect.Struct:
			for i := 0; i < v.NumField(); i++ {
				if !v.CanInterface() || !w.CanInterface() {
					// this is an unexported field; panic if try to access
					continue
				}
				switch comp(v.Field(i).Interface(), w.Field(i).Interface()) {
				case 1:
					return 1
				case -1:
					return -1
				}
			}
			return 0
		case reflect.Slice:
			for i := 0; i < v.Len(); i++ {
				if i == w.Len() {
					return 1
				}
				switch comp(v.Index(i).Interface(), w.Index(i).Interface()) {
				case -1:
					return -1
				case 1:
					return 1
				}
			}
			if w.Len() == v.Len() {
				return 0
			}
			return -1
		default:
			panic(fmt.Sprintf("The type %T is not comparable", a))
		}
	}
}

type KVPair struct {
	Key interface{}
	Val interface{}
}

type Map struct {
	tree *rbt.Tree
	sync.RWMutex
}

func NewMap() *Map {
	return &Map{rbt.NewWith(comp), sync.RWMutex{}}
}

func (m *Map) Put(key interface{}, val interface{}) {
	m.Lock()
	defer m.Unlock()
	m.tree.Put(key, val)
}

func (m *Map) Contains(key interface{}) bool {
	m.RLock()
	defer m.RUnlock()
	_, found := m.tree.Get(key)
	return found
}

func (m *Map) Get(key interface{}) interface{} {
	m.RLock()
	defer m.RUnlock()
	ret, _ := m.tree.Get(key)
	return ret
}

func (m *Map) Remove(key interface{}) {
	m.Lock()
	defer m.Unlock()
	m.tree.Remove(key)
}

func (m *Map) Clear() {
	m.Lock()
	defer m.Unlock()
	m.tree.Clear()
}

func (m *Map) IsEmpty() bool {
	m.RLock()
	defer m.RUnlock()
	return m.tree.Empty()
}

func (m *Map) Size() int {
	m.RLock()
	defer m.RUnlock()
	return m.tree.Size()
}

//Iterators (can be ranged over)
func (m *Map) Keys() <-chan interface{} {
	m.RLock()
	iter := m.tree.Iterator()
	ret := make(chan interface{})

	go func() {
		defer m.RUnlock()
		for iter.Next() {
			ret <- iter.Key()
		}
		close(ret)
	}()
	return ret
}

func (m *Map) Values() <-chan interface{} {
	m.RLock()
	iter := m.tree.Iterator()
	ret := make(chan interface{})

	go func() {
		defer m.RUnlock()
		for iter.Next() {
			ret <- iter.Value()
		}
		close(ret)
	}()
	return ret
}

func (m *Map) Iter() <-chan KVPair {
	m.RLock()
	iter := m.tree.Iterator()
	ret := make(chan KVPair)

	go func() {
		defer m.RUnlock()
		for iter.Next() {
			ret <- KVPair{iter.Key(), iter.Value()}
		}
		close(ret)
	}()
	return ret
}