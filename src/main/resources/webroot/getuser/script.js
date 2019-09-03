const users = [];

function addUser(user, users) {
  console.log(`Fetching user: ${user}`)
  fetch(`/users/${user}/info`)
    .then(response => {
      if (!response.ok) {
        throw Error(response.statusText);
      } else {
        return response;
      }
    })
    .then(response => response.json())
    .then(data => {
      console.log(`User arrived: ${data}`);
      users.unshift(data);
      displayUsers();
    })
    .catch(displayError)
    .finally(() => searchInput.disabled = false);
}

function displayError(error) {
  console.log(error);
  displayUsersWithError(error.message);
}

function formatDate(x) {
  let date = new Date(x);
  return date.toLocaleDateString();
}

function displayLoading() {
  displayUsersAnd(true, null)
}

function displayUsers() {
  displayUsersAnd(false, null)
}

function displayUsersWithError(errorMsg) {
  displayUsersAnd(false, errorMsg);
}

function displayUsersAnd(isLoading, errorMsg) {
  let html = users.map(user => {
    const userId = user._id;
    const userPhone = `<span class="hl">${user.phoneNumber}</span>`;
    return `
      <li>
        <span class="name">${userPhone} ${userId}</span>
        <span class="date">${formatDate(user.createdAt)}</span>
      </li>
    `;
  }).join('')
    +
    `<li>
    <span class="name">Phone number, User id</span>
    <span class="date">Date of creation</span>
  </li>`;
  if (errorMsg) {
    html = `
    <li>
      <span class="name">Error: ${errorMsg}</span>
    </li>
  ` + html;
  }
  if (isLoading) {
    html = `
    <li>
      <span class="name">Loading...</span>
    </li>
  ` + html;
  }
  suggestions.innerHTML = html;
}

const searchInput = document.querySelector('.search');
const suggestions = document.querySelector('.users');

searchInput.onkeypress = function (e) {
  if (!e) e = window.event;
  var keyCode = e.keyCode || e.which;
  if (keyCode == '13') {
    searchInput.disabled = true;
    displayLoading();
    addUser(this.value, users);
  }
}