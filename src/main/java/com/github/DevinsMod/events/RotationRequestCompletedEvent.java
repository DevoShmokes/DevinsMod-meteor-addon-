package com.github.DevinsMod.events;

import com.github.DevinsMod.utils.RotationRequest;

public class RotationRequestCompletedEvent {

    public static class Pre extends RotationRequestCompletedEvent {
        private static final Pre INSTANCE = new Pre();
        public RotationRequest request;

        public static Pre get(RotationRequest request) {
            Pre.INSTANCE.request = request;
            return Pre.INSTANCE;
        }
    }

    public static class Post extends RotationRequestCompletedEvent {
        private static final Post INSTANCE = new Post();
        public RotationRequest request;

        public static Post get(RotationRequest request) {
            Post.INSTANCE.request = request;
            return Post.INSTANCE;
        }
    }

}
