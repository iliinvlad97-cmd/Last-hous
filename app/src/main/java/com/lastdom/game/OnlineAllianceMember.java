package com.lastdom.game;

/** A fictional shelter membership and its durable invitation decision. */
final class OnlineAllianceMember {
  enum Invitation {
    PENDING,
    ACCEPTED,
    REJECTED
  }

  static final String PLAYER = "online_player";
  final String shelterId, invitationId;
  final Invitation invitation;
  final long resolveMinute;
  final boolean willAccept;
  final int reputation, contribution, successes;

  OnlineAllianceMember(
      String id,
      String invitationId,
      Invitation invitation,
      long resolve,
      boolean willAccept,
      int reputation,
      int contribution,
      int successes) {
    if (id == null
        || invitationId == null
        || invitation == null
        || resolve < 0
        || reputation < 0
        || reputation > 1_000_000
        || contribution < 0
        || contribution > 1_000_000
        || successes < 0
        || successes > OnlineCombatRules.MAX_HISTORY)
      throw new IllegalArgumentException("Invalid alliance member");
    if (invitation != Invitation.PENDING && (invitation == Invitation.ACCEPTED) != willAccept)
      throw new IllegalArgumentException("Inconsistent invitation decision");
    this.shelterId = id;
    this.invitationId = invitationId;
    this.invitation = invitation;
    this.resolveMinute = resolve;
    this.willAccept = willAccept;
    this.reputation = reputation;
    this.contribution = contribution;
    this.successes = successes;
  }

  OnlineAllianceMember resolve(long minute) {
    return invitation == Invitation.PENDING && minute >= resolveMinute
        ? new OnlineAllianceMember(
            shelterId,
            invitationId,
            willAccept ? Invitation.ACCEPTED : Invitation.REJECTED,
            resolveMinute,
            willAccept,
            reputation,
            contribution,
            successes)
        : this;
  }

  OnlineAllianceMember credit(int share) {
    return new OnlineAllianceMember(
        shelterId,
        invitationId,
        invitation,
        resolveMinute,
        willAccept,
        Math.addExact(reputation, shelterId.equals(PLAYER) ? 10 : 5),
        Math.addExact(contribution, share),
        Math.addExact(successes, 1));
  }
}
