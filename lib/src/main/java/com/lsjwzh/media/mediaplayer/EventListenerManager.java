package com.lsjwzh.media.mediaplayer;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by wenye on 15/11/28.
 */
public class EventListenerManager {
  final Hashtable<Class<? extends EventListener>, LinkedList<? extends EventListener>> mListenersMap
      = new Hashtable<Class<? extends EventListener>, LinkedList<? extends EventListener>>();


  public synchronized <T extends EventListener> void registerListener(@NonNull Class<T> listenerClass,
                                                                      @NonNull T listener) {
    Class clazzAsKey = findDirectSubClassOfEventListener(listenerClass);
    // if not any listeners,create a listener list
    if (!mListenersMap.containsKey(clazzAsKey)) {
      LinkedList<EventListener> listeners = new LinkedList<>();
      listeners.add(listener);
      mListenersMap.put(clazzAsKey, listeners);
    } else {
      LinkedList listeners = mListenersMap.get(clazzAsKey);
      listeners.add(listener);
    }
  }

  public synchronized <T extends EventListener> void registerListener(@NonNull T listener) {
    List<Class> eventClasses = findEventInterfaces(listener.getClass());
    for (Class c : eventClasses) {
      registerListener(c, listener);
    }
  }

  public synchronized void unregisterListener(@NonNull EventListener listener) {
    Class clazzAsKey = findDirectSubClassOfEventListener(listener.getClass());
    if (mListenersMap.containsKey(clazzAsKey)) {
      LinkedList listeners = mListenersMap.get(clazzAsKey);
      listeners.remove(listener);
    }
  }

  public synchronized void clearListeners() {
    mListenersMap.clear();
  }

  @NonNull
  public synchronized <T extends EventListener> List<T> getListeners(
      @NonNull Class<T> pTClass) {
    Class clazzAsKey = findDirectSubClassOfEventListener(pTClass);
    if (!mListenersMap.containsKey(clazzAsKey)) {
      LinkedList<EventListener> listeners = new LinkedList<>();
      mListenersMap.put(clazzAsKey, listeners);
    }
    return (List<T>) mListenersMap.get(clazzAsKey);
  }

  /**
   * ensure the result is direct sub class of EventListener
   *
   * @param pListenerClass
   * @return
   */
  private Class findDirectSubClassOfEventListener(Class pListenerClass) {
    if (pListenerClass.isInterface() && pListenerClass.getInterfaces().length > 0) {
      for (Class c : pListenerClass.getInterfaces()) {
        if (c == EventListener.class) {
          return pListenerClass;
        }
      }
    }
    throw new IllegalAccessError("can not find direct sub class of EventListener ");
  }

  private List<Class> findEventInterfaces(Class pListenerClass) {
    List<Class> classList = new ArrayList<>();
    for (Class clazz : pListenerClass.getInterfaces()) {
      if (EventListener.class.isAssignableFrom(clazz)) {
        classList.add(clazz);
      }
    }
    return classList;
  }
}
